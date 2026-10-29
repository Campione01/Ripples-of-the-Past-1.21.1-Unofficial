package rotp.addons.cream.ability;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.cream.CreamAddon;
import rotp.addons.cream.CreamConfig;
import rotp.addons.cream.CreamSpec;
import rotp.addons.cream.entity.CreamEntity;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;

public final class CreamChompAbility extends StandEntityAbility {
    public CreamChompAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ChompAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(
                ActionPhase.BUTTON_CHARGE,
                CreamSpec.CHOMP_CHARGE_TICKS);
        // 1.16 InitStands :73 standWindupDuration(20), standPerformDuration(20), no
        // standRecoveryTicks: the windup came after the hold, the task ended with the bite
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                CreamSpec.CHOMP_WINDUP_TICKS);
        setDefaultPhaseLength(
                ActionPhase.PERFORM,
                CreamSpec.CHOMP_PERFORM_TICKS);
        setDefaultPhaseLength(
                ActionPhase.RECOVERY,
                CreamSpec.CHOMP_RECOVERY_TICKS);
        resolveLevelToUnlock(CreamSpec.CHOMP_RESOLVE_LEVEL);
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
        noFinisherBarDecay = true;
    }

    /** Uses separate labels for an eligible target and an empty aim. */
    @Override
    public Component getName(Power<?> context) {
        LivingEntity user = context.getUser();
        Entity target = user == null ? null : chompTarget(user);
        return target == null
                ? abilityName(context, ".no_chompy")
                : abilityName(
                        context, ".chompy", target.getName().getString());
    }

    // 1.16 getTranslatedName: the first living entity other than the user on a 4-block ray from the user's eyes,
    // whatever the Stand's reach, canAttack, teams or the void state; blocks do not hide it. The user's own Stand
    // is never pickable on its own client.
    @Nullable
    private static Entity chompTarget(LivingEntity user) {
        StandEntity ownStand = summonedStand(user);
        List<ActionTarget> hits = HitResultUtil.clipMultipleEntities(
                user,
                CreamSpec.CHOMP_RANGE,
                entity -> entity instanceof LivingEntity && entity != user && entity != ownStand,
                0.0D,
                0.0D);
        return hits.isEmpty() ? null : hits.get(0).getMainEntity();
    }

    @Nullable
    private static StandEntity summonedStand(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null ? power.getSummonedStandEntity() : null;
    }

    // 1.16 CreamChomp had TargetRequirement.ENTITY: the click was refused
    // unless the crosshair was on an entity (silently), out of the Stand's
    // reach ("target_too_far") or not a living entity within 4 blocks of the
    // user ("no_target_chomp").
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        if (CreamEntity.isInVoidState(context.getUser())) {
            return ConditionCheck.NEGATIVE;
        }
        ConditionCheck base = super.checkSpecificConditions(context);
        return base.isPositive() ? checkCrosshairTarget(context) : base;
    }

    // 1.16 PowerBaseImpl.tickHeldAction asked for the crosshair target again
    // on every held tick; the hold, not holdOnly, ended once it left a valid one.
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        ConditionCheck base =
                super.checkHeldSpecificConditions(action, context);
        if (!base.isPositive()) {
            return base;
        }
        if (CreamEntity.isInVoidState(context.getUser())) {
            return ConditionCheck.NEGATIVE;
        }
        return checkCrosshairTarget(context);
    }

    private ConditionCheck checkCrosshairTarget(Power<?> context) {
        LivingEntity user = context.getUser();
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        StandEntity stand = summonedStand(user);
        Entity entity = aimedEntity(user, stand);
        if (entity == null) {
            return ConditionCheck.NEGATIVE;
        }
        return checkAimedEntity(user, stand, entity);
    }

    // 1.16 PowerBaseImpl.checkTarget first ran StandEntityAction
    // .overrideVanillaMouseTarget: a crosshair on a block without a collision
    // shape (grass, a torch) gave way to what the performer's collision ray
    // met.
    @Nullable
    private Entity aimedEntity(
            LivingEntity user, @Nullable StandEntity stand) {
        ActionTarget aimed = overridePressedTarget(
                user, crosshairTarget(user, stand));
        return aimed.getType() == ActionTarget.TargetType.ENTITY
                ? aimed.getMainEntity()
                : null;
    }

    private static ConditionCheck checkAimedEntity(LivingEntity user,
            @Nullable StandEntity stand, Entity entity) {
        // 1.16 Action.checkRangeAndTarget measured from the performer
        if (!ActionTargetRange.isEntityWithinRange(
                stand != null ? stand : user,
                entity,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!(entity instanceof LivingEntity living)
                || user.distanceTo(living) > CreamSpec.CHOMP_RANGE) {
            return ConditionCheck.createNegative("no_target_chomp");
        }
        return ConditionCheck.noMessage(
                ChompAction.canBeChomped(user, stand, living));
    }

    // The user's crosshair, or the Stand's under manual control, at the
    // reach of a Stand's aim; a crosshair meets blocks by their outlines.
    private static ActionTarget crosshairTarget(
            LivingEntity user, @Nullable StandEntity stand) {
        boolean manual = stand != null && stand.isManuallyControlled();
        LivingEntity aiming = manual ? stand : user;
        double reach;
        if (manual) {
            reach = stand.getAttributeValue(
                    Attributes.BLOCK_INTERACTION_RANGE);
        }
        else if (stand != null) {
            reach = StandEntityPunchAbility.userAimReach(user, stand);
        }
        else {
            reach = user.getAttributeValue(
                    Attributes.BLOCK_INTERACTION_RANGE) + 0.5D;
        }
        return HitResultUtil.clip(
                aiming.getEyePosition(),
                aiming.getLookAngle(),
                reach,
                reach,
                user.level(),
                entity -> entity != stand && entity != user,
                aiming,
                stand != null ? stand.getPrecision() : 0.0D,
                ClipContext.Block.OUTLINE);
    }

    // 1.16 StandEntityAction.checkStandTarget kept the entities the Stand may
    // attack; CreamChomp.checkTarget added the 4 blocks from the user and the
    // void states. The kind of target the task keeps is any living entity,
    // so that a refused one does not turn the Stand either.
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() instanceof LivingEntity;
    }

    @Override
    protected boolean keepsTaskTargetEntity(
            StandEntity standEntity, Entity entity) {
        LivingEntity user = standEntity.getUser();
        return user != null
                && !CreamEntity.isInVoidState(user)
                && user.distanceTo(entity) <= CreamSpec.CHOMP_RANGE
                && super.keepsTaskTargetEntity(standEntity, entity);
    }

    public static final class ChompAction extends EntityActionInstance {
        private LivingEntity target;
        private boolean armed;
        private boolean chomped;

        public ChompAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            if (!(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 took the press's mouse target: the Stand's aim, or the user's own pick for a Stand the press
            // summons, which has no aim yet
            LivingEntity user = getPowerUser();
            ActionTarget actionTarget = captureActionTargetFromAim(
                    user != null && !hasAimTarget(stand) ? user : stand);
            setStandFrontOffsetFromTarget(
                    stand,
                    actionTarget,
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(
                            CreamSpec.CHOMP_RANGE,
                            stand.getMaxRange()));
            keepStandAimedAtTarget(actionTarget);
            aimAs = AimingEntity.STAND;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null
                    ? StandPower.get(user)
                    : null;
            if (user == null || power == null) {
                startRecovery();
                return;
            }
            // 1.16 CreamChomp.standPerform stunned only the task's target, which StandEntityTask.keepTarget had
            // dropped for good on any tick it failed CreamChomp.checkTarget (4 blocks from the user), the windup's too
            Entity entity = getTaskTarget(level()).getEntity();
            if (entity instanceof LivingEntity living
                    && canBeChomped(user, stand, living)) {
                target = living;
                armed = true;
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        CreamSpec.CHOMP_STUN_TICKS,
                        0,
                        false,
                        false));
                StandUtil.playStandEntitySound(
                        stand,
                        CreamAddon.CREAM_CHOMP_START,
                        1.0F,
                        1.0F);
            }
            // 1.16 StandEntityTask paid staminaCost(340) after standPerform,
            // with or without a target; a short bar just drops to 0. With
            // nothing armed the 20 perform ticks still ran, doing nothing
            StandAbilityStamina.consume(
                    ability, power, CreamSpec.CHOMP_STAMINA, false);
        }

        @Override
        public void actionTick() {
            if (!armed
                    || target == null
                    || !(getPerformer() instanceof StandEntity stand)
                    || getPhase() != ActionPhase.PERFORM) {
                return;
            }
            // 1.16 standTickPerform moved and bit only the task's target: one the task dropped is neither
            if (!level().isClientSide()
                    && getTaskTarget(level()).getEntity() != target) {
                armed = false;
                target = null;
                return;
            }
            moveCream(stand, target);
            if (!level().isClientSide()
                    && !chomped
                    && getPhaseTick()
                            >= CreamSpec.CHOMP_PERFORM_TICKS - 1) {
                chomped = true;
                float damage = CreamSpec.CHOMP_DAMAGE
                        * (float) stand.getStandEfficiency();
                // 1.16 CreamChomp hurt the target directly, so the Stand
                // damage multiplier never scaled the bite.
                DamageSource source = makePunchDamageSource();
                ((DamageSourceModified) source)
                        .jojo_ripples$setFixedDamage(true);
                standEntityAttack(stand, target, source, damage);
                // 1.16 CreamChomp.standTickPerform: a world sound at the
                // Stand, whatever the hurt returned
                level().playSound(
                        null,
                        stand.getX(),
                        stand.getY(),
                        stand.getZ(),
                        CreamAddon.CREAM_CHOMP_SOUND.get(),
                        stand.getSoundSource(),
                        1.0F,
                        1.0F);
                // 1.16 CreamChomp.onTaskStopped set it only for a user not in Creative
                StandPower power = StandPower.get(getPowerUser());
                if (power != null && !power.isUserCreative()) {
                    power.setAbilityCooldown(
                            CreamAddon.CREAM_CHOMP_NAME,
                            CreamConfig.CHOMP_COOLDOWN.get());
                }
            }
        }

        private static boolean hasAimTarget(LivingEntity entity) {
            var aim = LivingComponentAction.getAim(entity);
            return aim != null && aim.getTarget() != null
                    && aim.getTarget().getType() != ActionTarget.TargetType.EMPTY;
        }

        // The charge keeps its own timeline phase, so the windup after it
        // holds the charge's last pose (1.16 PosedActionAnimation filled the
        // missing WINDUP pose with the end of the BUTTON_HOLD one).
        @Override
        public void extractAnim(
                LivingAnimState animVariables,
                LivingEntity performer,
                float partialTick) {
            super.extractAnim(animVariables, performer, partialTick);
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                animVariables.actionPhase = ActionPhase.BUTTON_CHARGE;
            }
        }

        @Override
        public void onButtonStopHold() {
            if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                LivingEntity performer = getPerformer();
                if (performer != null) {
                    LivingComponentAction.getComponent(performer)
                            .setAction(
                                    null,
                                    SyncType.TRACKING_AND_SELF);
                }
            }
        }

        // The HUD and the press also ask this before the Stand is summoned;
        // range is checked during the action. 1.16 canStandTargetEntity let
        // Chomp bite any entity its Stand may attack, enemy Stands included.
        static boolean canBeChomped(LivingEntity user,
                @Nullable StandEntity stand, Entity target) {
            return target instanceof LivingEntity living
                    && living != user
                    && living.isAlive()
                    && !living.isRemoved()
                    // vanilla canAttack refuses a Stand, which not everyone
                    // can see; StandEntity.canAttack decides for it
                    && (living instanceof StandEntity
                            || JojoModUtil.canHarm(user, living))
                    && standMayAttack(user, stand, living);
        }

        // 1.16 StandEntity.canAttack; with the Stand not yet summoned, the
        // same rules with its user as the attacker.
        private static boolean standMayAttack(LivingEntity user,
                @Nullable StandEntity stand, LivingEntity target) {
            if (stand != null) {
                return stand.canAttack(target);
            }
            if (!DamageUtil.isNotFriendlyFire(
                    user, StandUtil.getStandUser(target))) {
                return false;
            }
            if (target instanceof Animal) {
                if (target.isPassengerOfSameVehicle(user)) {
                    return false;
                }
                if (target instanceof TamableAnimal tameable
                        && tameable.getOwner() == user) {
                    return false;
                }
            }
            return true;
        }

        private static void moveCream(
                StandEntity stand, LivingEntity target) {
            double distance = Math.max(
                    0.75D, target.getBbWidth() * 0.65D);
            stand.setPos(
                    target.getX()
                            + target.getLookAngle().x * distance,
                    target.getY(),
                    target.getZ()
                            + target.getLookAngle().z * distance);
        }
    }
}

package rotp.addons.spicegirl.ability;

import javax.annotation.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;

import rotp.addons.spicegirl.SpiceGirlAddon;
import rotp.addons.spicegirl.entity.SpiceGirlStandEntity;
import rotp.addons.spicegirl.state.SpiceGirlElasticService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SoftenTargetAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 250.0F;
    public static final int DURATION_TICKS = 750;
    public static final int COOLDOWN_TICKS = 160;

    public SoftenTargetAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, SoftenTargetAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        partsRequired(StandPart.ARMS);
        cooldown(COOLDOWN_TICKS);
        // 1.16 StandEntityLightAttack.Builder: a press with Spice Girl away
        // summons her main arm only, an arms-only Stand adds it
        standAutoSummonMode(AutoSummonMode.MAIN_ARM);
    }

    // 1.16 SpiceGirlBounce extended StandEntityLightAttack: its windup,
    // perform and recovery came from the light attack's speed formula
    // (StandEntityLightAttack.getStandWindupTicks, getStandActionTicks,
    // getStandRecoveryTicks).
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide()
                && performer instanceof StandEntity stand) {
            EntityActionInstance current = stand.getCurStandAction();
            action.phasesLength.put(
                    ActionPhase.WINDUP,
                    StandStatFormulas.getLightAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter(),
                            stand.guardCounter(),
                            current == null || current.ability != this));
            float lightRecovery = StandStatFormulas.getLightAttackRecovery(
                    stand.getAttackSpeed(), stand.getFinisherMeter());
            action.phasesLength.put(ActionPhase.PERFORM, lightRecovery);
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    lightRecovery * (stand.isArmsOnlyMode() ? 2 : 4));
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base = super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        LivingEntity user = context != null ? context.getUser() : null;
        if (user == null) {
            return ConditionCheck.NEGATIVE;
        }
        SpiceGirlTargeting.Pressed pressed = SpiceGirlTargeting.pressTarget(
                this, user, SpiceGirlAddon.activeStand(user), user.level());
        return pressed.target() != null ? base : pressed.check();
    }

    // 1.16 SpiceGirlBounce.standKeepsTarget and getTargetRequirement:
    // a living entity
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == TargetType.ENTITY;
    }

    // 1.16 StandEntityLightAttack.lockOnTargetPosition, which
    // SpiceGirlBounce inherited
    @Override
    public boolean lockOnTargetPosition(
            @Nullable StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    // 1.16 SpiceGirlBounce.noAdheringToUserOffset: a fully summoned Stand
    // takes no offset from its user (standPerform put it at the target);
    // an arms-only one keeps its own
    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return !standEntity.isArmsOnlyMode();
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return !standEntity.isArmsOnlyMode();
    }

    public static final class SoftenTargetAction
            extends EntityActionInstance {
        public SoftenTargetAction(EntityActionType ability) {
            super(ability);
        }

        private boolean shoutSaid;
        private boolean playedSwingSound;

        @Override
        public void onActionSet(
                @Nullable EntityActionInstance previousAction) {
            super.onActionSet(previousAction);
            LivingEntity user = getPowerUser();
            if (user != null) {
                // 1.16 .shout(SG_ABILITY1_VOICE): PowerBaseImpl.onClickAction
                // said it at the accepted press (playVoiceLine), whatever came
                // of the perform; the flag keeps a restored action from
                // saying it twice
                if (!shoutSaid && !level().isClientSide()) {
                    shoutSaid = true;
                    sayShoutOf(ability, getPowerUser(), SpiceGirlAddon.SG_ABILITY1_VOICE);
                }
                captureActionTargetFromAim(user);
                // 1.16's task took the press's target, which the perform
                // struck, and turned the Stand to it
                if (!level().isClientSide()
                        && getPerformer() instanceof StandEntity stand) {
                    // 1.16 SpiceGirlBounce.onTaskSet, in place of the light
                    // attack's: an arms-only Stand about to swing its off hand
                    // keeps its main arm alone, and the hands do not alternate
                    if (stand.isArmsOnlyMode()
                            && stand.getPunchingHand() == InteractionHand.OFF_HAND) {
                        stand.setArmsOnlyMode(true, false);
                    }
                    LivingEntity target = SpiceGirlTargeting.findLookTarget(
                            ability, user, stand, level());
                    ActionTarget pressed = setActionTargetSnapshot(
                            target != null
                                    ? new ActionTarget(target)
                                    : ActionTarget.EMPTY);
                    if (target != null) {
                        keepStandAimedAtTarget(pressed);
                    }
                }
            }
        }

        // 1.16 StandEntityLightAttack.standTickPerform, which SpiceGirlBounce
        // inherited: the swing sound on clients in the perform, at its first
        // tick when it is shorter than 5 ticks, else with 5 ticks left
        @Override
        public void actionTick() {
            super.actionTick();
            if (!playedSwingSound && level().isClientSide()
                    && getPhase() == ActionPhase.PERFORM
                    && (getCurPhaseLength() < 5.0F
                            ? getPhaseTick() < 1.0F
                            : getPhaseTicksLeft() <= 5.0F)
                    && getPerformer() instanceof StandEntity stand) {
                playedSwingSound = true;
                if (ClientGlobals.canHearStand(stand)) {
                    level().playLocalSound(
                            stand.getX(),
                            stand.getEyeY(),
                            stand.getZ(),
                            ClientsideSoundsHelper.withStandSkin(
                                    ModSoundEvents.STAND_PUNCH_SWING.get(), stand),
                            stand.getSoundSource(),
                            1.0F,
                            0.9F + stand.getRandom().nextFloat() * 0.2F,
                            false);
                }
            }
        }

        // 1.16 StandEntityLightAttack.isCancelable: any action cut its
        // recovery short
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY;
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPowerUser()
                            instanceof ServerPlayer owner)
                    || !(getPerformer()
                            instanceof SpiceGirlStandEntity stand)
                    || SpiceGirlAddon.activeStand(owner) != stand) {
                return;
            }
            // 1.16 StandEntityLightAttack.getSounds, as the perform began: the
            // Stand sound only for an entity task target, a full Stand and an
            // empty finisher meter
            boolean performSound = getTaskTarget(level()).getType()
                            == TargetType.ENTITY
                    && !stand.isArmsOnlyMode()
                    && stand.getFinisherMeter() <= 0.0F;
            // no look of its own: 1.16 struck only the target its task kept,
            // which StandEntityTask.keepTarget dropped for good on any windup
            // tick it left the Stand's 8 blocks
            LivingEntity target = SpiceGirlTargeting.takenTarget(
                    owner, stand, getTaskTarget(level()), level());
            StandPower power = StandPower.get(owner);
            if (target != null) {
                // 1.16 standPerform: the Stand stands where its target is
                if (!stand.isArmsOnlyMode()) {
                    stand.setPos(target.position());
                }
                // 1.16 StandEntity.punch: aimWithThisOrUser struck the task's
                // target while the Stand could reach it, else what the user's
                // aim (then the Stand's) met within its reach, and the task
                // took what was struck
                ActionTarget struck = StandEntityPunchAbility
                        .getFreshPunchTarget(
                                stand,
                                new ActionTarget(target),
                                true,
                                CrosshairRule.BLOCK_OR_ENTITY);
                stand.turnTowardsStrike(StandEntityPunchAbility.strikeAim(
                        stand, struck, CrosshairRule.BLOCK_OR_ENTITY));
                setTaskTarget(struck);
                strike(stand, struck);
                // 1.16 softened the task's target, struck or not; an arms-only
                // Stand only within 3 blocks of it (EntityRange(user, 16)
                // always found the user itself, so it never stopped this)
                if (!stand.isArmsOnlyMode()
                        || target.distanceTo(stand) <= 3.0F) {
                    SpiceGirlElasticService.apply(
                            owner,
                            stand,
                            target,
                            DURATION_TICKS,
                            0);
                }
            }
            // 1.16 StandEntityTask paid after every perform, with a target or
            // without (standCanPerform), the result ignored; a short bar just
            // drops to 0.
            StandAbilityStamina.consume(
                    ability, power, STAMINA_COST, false);
            if (performSound) {
                StandUtil.playStandEntitySound(
                        stand,
                        SpiceGirlAddon.SG_ABILITY_2,
                        1.0F,
                        1.0F);
            }
        }

        // 1.16 StandEntity.attackTarget with StandEntityLightAttack's punches:
        // an entity takes the light attack's damage and its knockback with
        // the guard counter, and the Stand 0.1 finisher meter whether the hit
        // lands or not (StandEntityPunch.doHit); a block breaks by the
        // Stand's strength; a miss does nothing. The impact sound
        // (punchSound, the Builder's STAND_PUNCH_LIGHT) follows the hit: a
        // block's unless it breaks at once, a living entity's only if the hit
        // landed, any other entity's always; a miss has none
        private void strike(StandEntity stand, ActionTarget struck) {
            Vec3 impactSoundPos = StandEntityPunchAbility.playHitSound(
                            struck, level())
                    ? struck.getCenterPos()
                    : null;
            if (struck.getType() == TargetType.BLOCK) {
                StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(
                        struck, level(), stand, !isUserCreative());
                playImpactSound(stand, impactSoundPos);
                return;
            }
            Entity entity = struck.getType() == TargetType.ENTITY
                    ? struck.getMainEntity()
                    : null;
            if (entity == null) {
                return;
            }
            DamageSource source = makePunchDamageSource();
            if (entity instanceof LivingEntity
                    && source instanceof DamageSourceModified modified) {
                modified.jojo_ripples$modifyKnockback(
                        stand.guardCounter(), 1.0F);
            }
            boolean hurt = standEntityAttack(
                    stand,
                    entity,
                    source,
                    StandStatFormulas.getLightAttackDamage(
                            stand.getAttackDamage()));
            stand.addFinisherMeter(0.1F);
            if (hurt || !(entity instanceof LivingEntity)) {
                playImpactSound(stand, impactSoundPos);
            }
        }

        private void playImpactSound(
                StandEntity stand, @Nullable Vec3 pos) {
            if (pos != null && level() instanceof ServerLevel serverLevel) {
                StandUtil.broadcastSound(
                        serverLevel,
                        pos,
                        ModSoundEvents.STAND_PUNCH_LIGHT,
                        true,
                        StandPower.get(getPowerUser()),
                        stand.getSoundSource(),
                        1.0F,
                        1.0F);
            }
        }
    }
}

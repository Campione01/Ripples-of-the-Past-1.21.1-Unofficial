package rotp.addons.diverdown.ability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModSoundEvents;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
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
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class DeformDashAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 200.0F;
    public static final float DAMAGE = 7.0F;
    public static final double DASH_DISTANCE = 10.0D;
    public static final int DEBUFF_TICKS = 200;
    public static final int DEBUFF_AMPLIFIER = 1;

    public DeformDashAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DeformDashAction::new);
        partsRequired(StandPart.MAIN_BODY, StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 2);
        // 1.16 heavy punch finisher: shouts while sneaking too
        setPlaysVoiceLineOnSneak();
        // 1.16 StandEntityHeavyAttack.noFinisherBarDecay
        noFinisherBarDecay = true;
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide()
                && performer instanceof StandEntity stand) {
            action.phasesLength.put(
                    ActionPhase.PERFORM,
                    Math.max(
                            StandStatFormulas.getHeavyAttackWindup(
                                    stand.getAttackSpeed(),
                                    stand.getFinisherMeter()),
                            2));
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
        }
    }

    // 1.16 DiverDownDeformDash.replaceAction (049 :71-84): inside a target at a quarter of its health or less the heavy
    // key is the disassembly, whose own press condition then refuses a target above the Stand's attack damage; below
    // half a meter, which the running dash has always spent, it is the heavy punch again (:79-81)
    @Override
    public Ability replaceWithSubAbility(
            Power<?> context, AvailableAbilities abilities) {
        DiverDownStandEntity stand = DiverDownAddon.activeStand(
                context != null ? context.getUser() : null);
        if (abilities != null
                && MobDisassembleAbility.replacesDeformDash(stand)) {
            Ability disassemble = abilities.getContextVariation(
                    DiverDownAddon.DISASSEMBLE_MOB_NAME);
            if (disassemble instanceof MobDisassembleAbility
                    && disassemble.isAbilityAvailable(context)) {
                return disassemble;
            }
        }
        if (stand != null && stand.getFinisherMeter() < 0.5F) {
            Ability heavy = context.getAbility(DiverDownAddon.HEAVY_PUNCH);
            // the heavy punch's own replacement (its grab variation) still applies: the key is resolved in one pass
            if (heavy != null && heavy != this
                    && heavy.isAbilityAvailable(context)) {
                return heavy.replaceWithSubAbility(context, abilities);
            }
        }
        return super.replaceWithSubAbility(context, abilities);
    }

    // 1.16 DiverDownDeformDash.checkStandTarget was NEGATIVE for every target (049 :155-157): PowerBaseImpl.checkTarget
    // emptied the pressed one, so the Stand dashed along its user's look instead of turning to it
    @Override
    public boolean takesPressedTarget(
            StandEntity standEntity, ActionTarget target) {
        return false;
    }

    @Override
    public boolean noAdheringToUserOffset(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean noAdheringToUserOffsetClientFallback(
            StandEntity standEntity) {
        return true;
    }

    @Override
    public boolean lockStandManualMovement(
            StandPower standPower, StandEntity standEntity) {
        return true;
    }

    public static final class DeformDashAction
            extends EntityActionInstance {
        private final Set<Integer> hitEntities = new HashSet<>();
        private Vec3 dashStep = Vec3.ZERO;
        private Vec3 dashForward = Vec3.ZERO;
        private ActionTarget dashTarget = ActionTarget.EMPTY;
        private boolean retractAfterDash;

        public DeformDashAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                ActionTarget target = captureActionTargetFromAim(stand);
                keepStandAimedAtTarget(target);
                retractAfterDash =
                        stand.followingUserIsEnabled()
                                && !stand.isManuallyControlled()
                                && !stand.isBeingRetracted();
                // 1.16 StandEntityHeavyAttack.onClick and onTaskSet: the finisher value is taken, the recovery follows
                // it and the meter loses 0.51; a failed input that puts the dash back keeps what it took
                if (!stand.level().isClientSide() && !isRestoringInputLifecycle()) {
                    stand.setHeavyPunchFinisher();
                    phasesLength.put(
                            ActionPhase.RECOVERY,
                            StandStatFormulas.getHeavyAttackRecovery(
                                    stand.getAttackSpeed(),
                                    stand.getLastHeavyFinisherValue()));
                    stand.addFinisherMeter(-0.51F, 0);
                }
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            if (newPhase != ActionPhase.PERFORM
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            LivingEntity user = getPowerUser();
            if (!level().isClientSide()) {
                if (user == null) {
                    startRecovery();
                    return;
                }
                sayShoutOf(
                        ability,
                        user,
                        DiverDownAddon.ANASUI_DIVER_DEFORM_DASH);
                StandUtil.playStandEntitySound(
                        stand,
                        DiverDownAddon.DIVER_DOWN_DEFORM_DASH.get(),
                        1.0F,
                        1.0F);
                // 1.16 StandEntityTask paid the cost as the perform began
                // and ignored the result: a short bar only drops to 0.
                StandAbilityStamina.consume(
                        ability,
                        StandPower.get(user),
                        STAMINA_COST,
                        false);
            }

            dashForward = dashLook(stand, user);
            hitEntities.clear();
            if (!level().isClientSide()
                    && retractAfterDash
                    && stand.getAttackSpeed() < 24.0F
                    && user != null) {
                Vec3 userForward = horizontalLook(user);
                stand.setPos(
                        user.getX() + userForward.x,
                        stand.getY(),
                        user.getZ() + userForward.z);
            }
            float performTicks = Math.max(
                    phasesLength.getFloat(ActionPhase.PERFORM), 1.0F);
            dashStep =
                    dashForward.scale(DASH_DISTANCE / performTicks);
            // 1.16 StandEntityHeavyAttack.standPerform: punch(task.getTarget()) as the perform began, and the dash's
            // task held no target (checkStandTarget NEGATIVE), so the strike aimed again
            dashTarget = !level().isClientSide()
                    ? hitReaimedDashTarget(level(), stand)
                    : ActionTarget.EMPTY;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }

            boolean moveForward =
                    getCurPhaseLength() <= 0
                            || (getPhaseTick() + 1.0F)
                                    / getCurPhaseLength() <= 0.5F;
            boolean moved =
                    stand.getDeltaMovement().lengthSqr() > 1.0E-7D;
            if (!level().isClientSide()) {
                if (moveForward) {
                    hitDashTargets(level(), stand);
                }
                else if (moved) {
                    // 1.16 struck the task's target here, which the dash never kept
                    hitReaimedDashTarget(level(), stand);
                }
                if (getPhaseTicksLeft() <= 1 && retractAfterDash) {
                    stand.retract();
                }
            }
            stand.setDeltaMovement(moveForward ? dashStep : Vec3.ZERO);
            stand.hurtMarked = true;
        }

        @Override
        public void actionPerformEnd() {
            if (getPerformer() instanceof StandEntity stand) {
                stand.setDeltaMovement(Vec3.ZERO);
                if (!level().isClientSide() && retractAfterDash) {
                    stand.retract();
                }
            }
        }

        @Override
        public void onActionCleared(EntityActionInstance newAction) {
            if (getPerformer() instanceof StandEntity stand) {
                stand.setDeltaMovement(Vec3.ZERO);
            }
        }

        // 1.16 DiverDownDeformDash.isChainable and StandEntityHeavyAttack.isFreeRecovery (a finisher): any action, this
        // one too, cancels its recovery
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY;
        }

        private void hitDashTargets(Level level, StandEntity stand) {
            double reach = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            List<ActionTarget> targets = HitResultUtil.clipMultipleTargets(
                    stand,
                    reach,
                    entity -> StandEntityPunchAbility.canStandHit(
                            stand, entity),
                    0.25D,
                    stand.getPrecision());
            if (targets.isEmpty()) {
                // 1.16 punched the miss of a ray that met nothing in the air
                hitReaimedDashTarget(level, stand);
                return;
            }
            for (ActionTarget target : targets) {
                hitDashTarget(level, stand, target);
            }
        }

        /**
         * 1.16 StandEntity.punch with no target: aimWithThisOrUser aimed again (the user's precision ray, else the
         * Stand's own, under manual control only the Stand's), turned the Stand toward what that met and struck it if
         * it lay in the Stand's reach. Returns what it aimed at.
         */
        private ActionTarget hitReaimedDashTarget(Level level, StandEntity stand) {
            ActionTarget aimed = StandEntityPunchAbility.strikeAim(
                    stand, ActionTarget.EMPTY, StandEntityPunchAbility.CrosshairRule.NONE);
            stand.turnTowardsStrike(aimed);
            ActionTarget inReach = StandEntityPunchAbility.validatePunchTarget(stand, aimed);
            if (!inReach.isEmpty(level)) {
                strikeDashTarget(level, stand, inReach);
            }
            return aimed.isEmpty(level)
                    ? ActionTarget.EMPTY
                    : aimed.copy().resolveEntityId(level);
        }

        private void hitDashTarget(
                Level level,
                StandEntity stand,
                ActionTarget target) {
            target = target.resolveEntityId(level);
            if (target.isEmpty(level)) {
                return;
            }
            // 1.16 StandEntity.punch turned the Stand toward each target it struck
            stand.turnTowardsStrike(target);
            strikeDashTarget(level, stand, target);
        }

        /**
         * 1.16 DiverDownDeformDash.punchEntity: a StandEntityHeavyAttack hit (HeavyPunchInstance: the heavy knockback,
         * 10 ticks of Stand invulnerability, so each entity takes the dash once, the wall impact and a hit Stand's
         * barrage stopped) of 7 damage and no impact sound, the debuffs given before the hit whether it lands or not;
         * a block on the ray is struck as the heavy punch strikes one (StandEntityHeavyAttack.punchBlock).
         */
        private void strikeDashTarget(
                Level level,
                StandEntity stand,
                ActionTarget target) {
            switch (target.getType()) {
                case ENTITY -> {
                    Entity candidate = target.getMainEntity();
                    if (candidate == null) {
                        return;
                    }
                    if (candidate instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SLOWDOWN,
                                DEBUFF_TICKS,
                                DEBUFF_AMPLIFIER));
                        living.addEffect(new MobEffectInstance(
                                MobEffects.WEAKNESS,
                                DEBUFF_TICKS,
                                DEBUFF_AMPLIFIER));
                        living.addEffect(new MobEffectInstance(
                                MobEffects.DIG_SLOWDOWN,
                                DEBUFF_TICKS,
                                DEBUFF_AMPLIFIER));
                    }
                    if (!hitEntities.add(candidate.getId())) {
                        return;
                    }
                    DamageSource damageSource = makePunchDamageSource();
                    DamageSourceModified heavy = (DamageSourceModified) damageSource;
                    heavy.jojo_ripples$setStandInvulTicks(10);
                    heavy.jojo_ripples$modifyKnockback(
                            StandStatFormulas.getHeavyAttackKnockback(
                                    stand.getAttackDamage(),
                                    stand.getLastHeavyFinisherValue()),
                            1);
                    boolean admittedObject = !(candidate instanceof LivingEntity)
                            && stand.canAttackEntity(candidate);
                    boolean hurt = standEntityAttack(
                            stand,
                            candidate,
                            damageSource,
                            DAMAGE);
                    // 1.16 StandEntityPunch.knockbackTarget: a non-living target admitted to the hit is set moving away
                    // from the Stand at half the heavy knockback, whether the hit landed or not
                    if (admittedObject) {
                        Vec3 toTarget = candidate.position().subtract(stand.position());
                        float yaw = (float) -Mth.atan2(toTarget.x, toTarget.z) * MathUtil.RAD_TO_DEG;
                        candidate.setDeltaMovement(new Vec3(0.0D, 0.0D,
                                StandStatFormulas.getHeavyAttackKnockback(
                                        stand.getAttackDamage(),
                                        stand.getLastHeavyFinisherValue()) * 0.5F)
                                .yRot(-yaw * MathUtil.DEG_TO_RAD));
                    }
                    if (hurt) {
                        afterHeavyHit(stand, candidate);
                    }
                }
                case BLOCK ->
                        StandEntityHeavyPunchAbility.StandEntityHeavyPunch
                                .hitBlockAsHeavyPunch(
                                        target,
                                        level,
                                        stand,
                                        !isUserCreative(),
                                        ModSoundEvents.STAND_PUNCH_HEAVY);
                default -> {
                }
            }
            punchedTarget = target;
        }

        // 1.16 HeavyPunchInstance.afterAttack: a hit Stand's barrage stops, and the knocked-back entity (a Stand's
        // user) gets the wall impact
        private static void afterHeavyHit(StandEntity stand, Entity target) {
            EntityActionAbility.onHitByHeavyAttack(target);
            Entity knockedBack = target;
            if (target instanceof StandEntity targetStand && targetStand.getUser() != null) {
                knockedBack = targetStand.getUser();
            }
            KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(knockedBack);
            if (impact != null) {
                impact.onPunchSetKnockbackImpact(knockedBack.getDeltaMovement(), stand)
                        .withImpactExplosion(
                                Math.max(StandEntityHeavyPunchAbility.calcExplosionRadius(stand) - 0.5F, 0),
                                null,
                                0);
            }
        }

        ActionTarget dashTarget() {
            return dashTarget;
        }

        private static Vec3 horizontalLook(LivingEntity entity) {
            Vec3 look =
                    entity.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            return look.lengthSqr() < 1.0E-6D
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : look.normalize();
        }

        private static Vec3 dashLook(
                StandEntity stand, LivingEntity fallback) {
            Vec3 look = stand.getLookAngle();
            if (look.lengthSqr() < 1.0E-6D && fallback != null) {
                look = fallback.getLookAngle();
            }
            return look.lengthSqr() < 1.0E-6D
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : look.normalize();
        }
    }
}

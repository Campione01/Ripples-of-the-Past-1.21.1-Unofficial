package rotp.addons.neo_stand.ability;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FingerSweepAbility extends StandEntityAbility {
    public static final int PERFORM_TICKS = 10;
    public static final double RANGE = 10.0D;
    // 1.16 registered it as a plain StandEntityHeavyAttack: the builder's
    // DEFAULT_STAMINA_COST.
    public static final float STAMINA_COST = 50.0F;

    public FingerSweepAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, FingerSweepAction::new);
        setDefaultPhaseLength(ActionPhase.WINDUP,
                windupTicks(StandStatFormulas.getHeavyAttackWindup(12, 0)));
        setDefaultPhaseLength(ActionPhase.PERFORM, PERFORM_TICKS);
        setDefaultPhaseLength(ActionPhase.RECOVERY,
                StandStatFormulas.getHeavyAttackRecovery(12, 0));
        noFinisherBarDecay = true;
        standAutoSummonMode(AutoSummonMode.FULL);
        partsRequired(StandPart.ARMS);
    }

    // 1.16 021 aa/aa/ad.java :67-69: two ticks shorter than the heavy
    // attack's windup, at least one
    static int windupTicks(int heavyWindup) {
        return Math.max(heavyWindup - 2, 1);
    }

    // 1.16 StandEntityHeavyAttack.getStandWindupTicks (:186-189) by the
    // finisher meter before the press spent it, and getStandRecoveryTicks
    // (:191-194), set again from the press's finisher value in onActionSet
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
            action.phasesLength.put(ActionPhase.WINDUP,
                    windupTicks(StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter())));
            action.phasesLength.put(ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
        }
    }

    // 1.16 ad.java :60-65 asked StandEntityHeavyAttack.checkStandConditions
    // (:125-127): no press, and no message, while the Stand cannot attack in
    // melee
    @Override
    protected ConditionCheck checkStandEntityConditions(
            StandPower standPower, StandEntity standEntity) {
        ConditionCheck check = super.checkStandEntityConditions(
                standPower, standEntity);
        return check.isPositive()
                ? ConditionCheck.noMessage(standEntity.canAttackMelee())
                : check;
    }

    // 1.16 StandEntityHeavyAttack.canFollowUpBarrage (:222-225): a barrage in
    // its recovery gave way to it (StandEntityMeleeBarrage.isCancelable
    // :181-191), as the core barrage does to its heavy punch
    @Override
    public boolean shouldBufferInput(LivingComponentAction performerAction) {
        return !followsUpBarrage(performerAction.getAction())
                && super.shouldBufferInput(performerAction);
    }

    static boolean followsUpBarrage(@Nullable EntityActionInstance current) {
        return current != null
                && current.ability instanceof StandEntityBarrageAbility
                && current.getPhase() == ActionPhase.RECOVERY;
    }

    // 1.16 StandEntityHeavyAttack.standKeepsTarget: the task keeps an entity
    // it was pressed at, checked on every tick
    @Override
    protected boolean keepsTaskTargetType(
            StandEntity standEntity, ActionTarget target) {
        return target.getType() == ActionTarget.TargetType.ENTITY;
    }

    // 1.16 StandEntityHeavyAttack.lockOnTargetPosition: the Stand turns to
    // its target, never its offset
    @Override
    public boolean lockOnTargetPosition(
            @Nullable StandPower standPower,
            StandEntity standEntity,
            EntityActionInstance action) {
        return false;
    }

    public static final class FingerSweepAction
            extends EntityActionInstance {
        private final Set<UUID> hitTargets = new HashSet<>();

        public FingerSweepAction(EntityActionType ability) {
            super(ability);
        }

        /*
         * Server: 1.16 StandEntityHeavyAttack took the press's target (a
         * pressed block without a collision shape giving way, the Stand's
         * canAttack asked) as its task's, kept only an entity, turned the
         * Stand to it, so the fan follows it, and stood at front3dOffset of
         * it.
         */
        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            // 1.16 StandEntityHeavyAttack.onTaskSet (:139), on either side; a
            // failed input that puts the sweep back keeps the hand it took
            if (getPerformer() instanceof StandEntity swinging
                    && !isRestoringInputLifecycle()) {
                swinging.alternateHands();
            }
            if (level().isClientSide()
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            // 1.16 StandEntityHeavyAttack.onClick and onTaskSet, once: a failed
            // input that puts the sweep back keeps the finisher it took and
            // spent when it was first set, as the core heavy punch does
            if (!isRestoringInputLifecycle()) {
                stand.setHeavyPunchFinisher();
                phasesLength.put(ActionPhase.RECOVERY,
                        StandStatFormulas.getHeavyAttackRecovery(
                                stand.getAttackSpeed(),
                                stand.getLastHeavyFinisherValue()));
                stand.addFinisherMeter(-0.51F, 0);
            }
            ActionTarget pressed = captureActionTargetFromAim(stand);
            ActionTarget kept = ActionTarget.EMPTY;
            if (pressed.getType() == ActionTarget.TargetType.ENTITY) {
                kept = pressed;
                keepStandAimedAtTarget(kept);
            }
            setStandFrontOffsetFromTarget(stand, kept,
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
        }

        // 1.16 StandEntityHeavyAttack.getOffsetFromUser, asked on every
        // tick: front3dOffset of the task's target, else of the user's look
        @Override
        public void updateStandOffset(StandEntity stand) {
            StandEntityBarrageAbility.refreshFrontOffset(this, stand,
                    getTaskTarget(stand.level()),
                    Math.min(0.5D, stand.getEffectiveRange()),
                    Math.min(2.0D, stand.getMaxRange()));
        }

        // 1.16 StandEntityTask: the cost is paid at perform start with the
        // result ignored; a short bar just drops to 0.
        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandAbilityStamina.consume(
                    ability,
                    user != null ? StandPower.get(user) : null,
                    STAMINA_COST,
                    false);
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    || getPhase() != ActionPhase.PERFORM
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            double segment = 180.0D / PERFORM_TICKS;
            double center = -90.0D
                    + (getPhaseTick() + 0.5D) * segment;
            Vec3 forward = stand.getLookAngle()
                    .multiply(1.0D, 0.0D, 1.0D);
            if (forward.lengthSqr() < 1.0E-6D && user != null) {
                forward = user.getLookAngle()
                        .multiply(1.0D, 0.0D, 1.0D);
            }
            if (forward.lengthSqr() < 1.0E-6D) {
                forward = new Vec3(0.0D, 0.0D, 1.0D);
            }
            forward = forward.normalize();
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
            AABB area = stand.getBoundingBox().inflate(RANGE);
            for (LivingEntity target : level().getEntitiesOfClass(
                    LivingEntity.class,
                    area,
                    candidate -> candidate != stand
                            && candidate != user
                            && candidate.isAlive()
                            && stand.canAttack(candidate))) {
                if (hitTargets.contains(target.getUUID())) {
                    continue;
                }
                Vec3 delta = target.getBoundingBox().getCenter()
                        .subtract(stand.getBoundingBox().getCenter());
                if (delta.lengthSqr() > RANGE * RANGE
                        || delta.lengthSqr() < 1.0E-6D) {
                    continue;
                }
                Vec3 horizontal =
                        delta.multiply(1.0D, 0.0D, 1.0D);
                if (horizontal.lengthSqr() < 1.0E-6D) {
                    continue;
                }
                horizontal = horizontal.normalize();
                double angle = Math.toDegrees(Math.atan2(
                        horizontal.dot(right),
                        horizontal.dot(forward)));
                if (Math.abs(angle - center) > segment * 0.5D
                        || Math.abs(angle) > 90.0D) {
                    continue;
                }
                DamageSource source = makePunchDamageSource();
                if (source instanceof DamageSourceModified modified) {
                    modified.jojo_ripples$modifyKnockback(0.75F, 1);
                    modified.jojo_ripples$setStandInvulTicks(10);
                }
                float damage = StandStatFormulas.getHeavyAttackDamage(
                        stand.getAttackDamage());
                if (standEntityAttack(
                        stand, target, source, damage)) {
                    hitTargets.add(target.getUUID());
                }
            }
        }
    }
}

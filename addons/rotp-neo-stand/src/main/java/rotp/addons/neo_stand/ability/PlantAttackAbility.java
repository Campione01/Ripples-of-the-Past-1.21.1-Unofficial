package rotp.addons.neo_stand.ability;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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

import rotp.addons.neo_stand.entity.PlantAttackEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PlantAttackAbility extends StandEntityAbility {
    public static final float STAMINA_COST = 50.0F;

    public PlantAttackAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId) {
        super(abilityType, abilityId, PlantAttackAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(
                ActionPhase.WINDUP,
                StandStatFormulas.getHeavyAttackWindup(12, 0));
        setDefaultPhaseLength(ActionPhase.PERFORM, 6);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
        noFinisherBarDecay = true;
        partsRequired(StandPart.ARMS);
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
                    ActionPhase.WINDUP,
                    StandStatFormulas.getHeavyAttackWindup(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(),
                            stand.getFinisherMeter()));
        }
    }

    // 1.16 StandEntityHeavyAttack.canFollowUpBarrage (:222-225), not
    // overridden by aa.java: a barrage's recovery gave way to it. Its
    // checkStandConditions (aa.java :46-51) did not ask canAttackMelee.
    @Override
    public boolean shouldBufferInput(LivingComponentAction performerAction) {
        return !FingerSweepAbility.followsUpBarrage(performerAction.getAction())
                && super.shouldBufferInput(performerAction);
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

    public static final class PlantAttackAction
            extends EntityActionInstance {
        public PlantAttackAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                // 1.16 StandEntityHeavyAttack.onTaskSet (:139), on either
                // side; a failed input that puts it back keeps its hand
                if (!isRestoringInputLifecycle()) {
                    stand.alternateHands();
                }
                // the finisher bookkeeping runs once: a failed input that puts
                // it back keeps the finisher it took and spent when it was
                // first set, as the core heavy punch does
                if (!level().isClientSide()) {
                    if (!isRestoringInputLifecycle()) {
                        stand.setHeavyPunchFinisher();
                    }
                    keepPressedEntity(stand);
                }
                if (!isRestoringInputLifecycle()) {
                    phasesLength.put(ActionPhase.RECOVERY,
                            StandStatFormulas.getHeavyAttackRecovery(
                                    stand.getAttackSpeed(),
                                    stand.getLastHeavyFinisherValue()));
                    if (!level().isClientSide()) {
                        stand.addFinisherMeter(-0.51F, 0);
                    }
                }
            }
        }

        /*
         * Server: 1.16 StandEntityHeavyAttack took the press's target (a
         * pressed block without a collision shape giving way, the Stand's
         * canAttack asked) as its task's, kept only an entity, turned the
         * Stand to it and stood at front3dOffset of it.
         */
        private void keepPressedEntity(StandEntity stand) {
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

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(getPerformer()
                            instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            Vec3 forward = stand.getLookAngle().normalize();
            Vec3 origin = stand.position().add(
                    0.0D,
                    stand.getBbHeight() * 0.5D,
                    0.0D);
            level().addFreshEntity(new PlantAttackEntity(
                    level(),
                    origin,
                    forward,
                    PlantAttackEntity.DEFAULT_LENGTH,
                    PlantAttackEntity.DEFAULT_WIDTH,
                    PlantAttackEntity.DEFAULT_LIFETIME,
                    user != null ? user.getId() : stand.getId()));
            // 1.16 StandEntityTask: the cost is paid after the strip is out;
            // a short bar just drops to 0.
            StandAbilityStamina.consume(
                    ability,
                    user != null ? StandPower.get(user) : null,
                    STAMINA_COST,
                    false);
        }
    }
}

package rotp.addons.tusk.ability;

import rotp.core.init.ModParticles;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.diagnostic.TuskActionDiagnostics;
import rotp.addons.tusk.state.TuskNailState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class TuskInfiniteRotationAbility
        extends StandEntityAbility {
    public static final float STAMINA_COST = 425.0F;
    private static final double DASH_DISTANCE = 10.0D;

    public TuskInfiniteRotationAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, InfiniteRotationAction::new);
        usageGroup = AbilityUsageGroup.COMBAT;
        setDefaultPhaseLength(ActionPhase.WINDUP, 0);
        setDefaultPhaseLength(ActionPhase.PERFORM, 2);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 12);
        partsRequired(StandPart.MAIN_BODY);
        noFinisherBarDecay = true;
    }

    @Override
    public boolean addToControlSchemeEditing() {
        return false;
    }

    @Override
    public boolean isAbilityAvailable(Power<?> context) {
        return super.isAbilityAvailable(context)
                && context != null
                && TuskAddon.hasInfiniteRotationCharge(
                        context.getUser());
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (performer instanceof StandEntity stand) {
            float finisher = stand.getLastHeavyFinisherValue();
            action.phasesLength.put(
                    ActionPhase.PERFORM,
                    Math.max(
                            StandStatFormulas.getHeavyAttackWindup(
                                    stand.getAttackSpeed(),
                                    finisher) * 2,
                            2));
            action.phasesLength.put(
                    ActionPhase.RECOVERY,
                    StandStatFormulas.getHeavyAttackRecovery(
                            stand.getAttackSpeed(), finisher));
        }
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

    public static final class InfiniteRotationAction
            extends EntityActionInstance {
        private Vec3 dashStep = Vec3.ZERO;
        private boolean transferred;
        private boolean started;
        private boolean completed;

        public InfiniteRotationAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPerformer() instanceof StandEntity stand) {
                // A port addition the owner kept (2026-10-16): 1.16's
                // checkStandTarget refused every pressed target, so its Stand
                // neither turned to the mob nor handed it the spin.
                ActionTarget target = captureActionTargetFromAim(stand);
                keepStandAimedAtTarget(target);
                aimAs = AimingEntity.STAND;
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
                // 1.16 StandEntityTask: the cost is paid with the result
                // ignored; a short bar just drops to 0.
                StandAbilityStamina.consume(
                        ability, StandPower.get(user), STAMINA_COST, false);
                started = true;
                sayShoutOf(
                        ability,
                        user,
                        TuskAddon.JOHNNY_HEAVY_ATTACK);
                StandUtil.playStandEntitySound(
                        stand,
                        TuskAddon.TUSK_CHIMIMI,
                        1.0F,
                        1.0F);
                if (stand.followingUserIsEnabled()
                        && stand.getAttackSpeed() < 24.0F) {
                    Vec3 forward = horizontalLook(user);
                    stand.setPos(
                            user.getX() + forward.x,
                            stand.getY(),
                            user.getZ() + forward.z);
                }
            }

            float performTicks = Math.max(
                    phasesLength.getFloat(ActionPhase.PERFORM), 1.0F);
            dashStep = actionLook(stand, user)
                    .scale(DASH_DISTANCE / performTicks);
            transferred = false;
            completed = false;
        }

        @Override
        public void actionTick() {
            if (getPhase() != ActionPhase.PERFORM
                    || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = getPowerUser();
            TuskActionDiagnostics.recordPerformTick(
                    level().isClientSide(),
                    stand.getUUID(),
                    user != null ? user.getUUID() : null);

            boolean moveForward =
                    getCurPhaseLength() <= 0.0F
                            || getPhaseRatio() <= 0.5F;
            boolean terminalTick = getPhaseTicksLeft() <= 1.0F;
            if (!level().isClientSide() && !transferred) {
                ServerLevel serverLevel = (ServerLevel) level();
                if (moveForward) {
                    transferToFirstTarget(serverLevel, stand);
                }
                if (!transferred && (!moveForward || terminalTick)) {
                    transferToActionTarget(serverLevel, stand);
                }
            }
            stand.setNoPhysics(moveForward);
            stand.setDeltaMovement(
                    moveForward ? dashStep : Vec3.ZERO);
            stand.hurtMarked = true;

            if (terminalTick) {
                complete(stand);
            }
        }

        @Override
        public void actionPerformEnd() {
            if (getPerformer() instanceof StandEntity stand) {
                if (!level().isClientSide() && !transferred) {
                    transferToActionTarget(
                            (ServerLevel) level(), stand);
                }
                stand.setNoPhysics(false);
                stand.setDeltaMovement(Vec3.ZERO);
                complete(stand);
            }
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (getPerformer() instanceof StandEntity stand) {
                stand.setNoPhysics(false);
                stand.setDeltaMovement(Vec3.ZERO);
            }
        }

        private void transferToFirstTarget(
                ServerLevel level, StandEntity stand) {
            double reach = stand.getAttributeValue(
                    Attributes.ENTITY_INTERACTION_RANGE);
            LivingEntity user = getPowerUser();
            for (ActionTarget target :
                    HitResultUtil.clipMultipleTargets(
                            stand,
                            reach,
                            entity -> entity instanceof LivingEntity
                                    && entity != stand
                                    && entity != user
                                    && entity.isAlive(),
                            0.25D,
                            stand.getPrecision())) {
                if (transferToTarget(
                        level, stand, user, target)) {
                    return;
                }
            }
        }

        private void transferToActionTarget(
                ServerLevel level, StandEntity stand) {
            transferToTarget(
                    level,
                    stand,
                    getPowerUser(),
                    getActionTargetSnapshot(level));
        }

        private boolean transferToTarget(
                ServerLevel level,
                StandEntity stand,
                LivingEntity user,
                ActionTarget target) {
            ActionTarget resolved = target.resolveEntityId(level);
            Entity entity = resolved.getMainEntity();
            if (!(entity instanceof LivingEntity living)
                    || living == stand
                    || living == user
                    || !living.isAlive()) {
                return false;
            }
            toggleEffect(level, living, user, stand);
            punchedTarget = resolved;
            transferred = true;
            return true;
        }

        private void toggleEffect(
                ServerLevel level,
                LivingEntity target,
                LivingEntity source,
                StandEntity stand) {
            // The canonical zero-damage punch exits before hurt or shield
            // cooldown handling. Its outcome is this effect toggle.
            level.sendParticles(
                    ModParticles.LIGHT_MODE_FLASH.get(),
                    target.getX(),
                    target.getY(0.5D),
                    target.getZ(),
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
            StandUtil.playStandEntitySound(
                    stand,
                    TuskAddon.TUSK_PUNCH_HEAVY,
                    1.0F,
                    1.0F);
            if (target.hasEffect(TuskAddon.INFINITE_ROTATION)) {
                target.removeEffect(TuskAddon.INFINITE_ROTATION);
                return;
            }
            target.addEffect(
                    new MobEffectInstance(
                            TuskAddon.INFINITE_ROTATION,
                            Integer.MAX_VALUE,
                            0,
                            true,
                            true,
                            true),
                    source);
            TuskActionDiagnostics.recordImpact(
                    stand.getUUID(),
                    source != null ? source.getUUID() : null,
                    target.getUUID());
        }

        private void complete(StandEntity stand) {
            if (!started
                    || completed
                    || level().isClientSide()) {
                return;
            }
            completed = true;
            LivingEntity user = getPowerUser();
            if (stand.followingUserIsEnabled()
                    && user instanceof Player player) {
                stand.retract();
                TuskNailState state = TuskAddon.nailState(player);
                if (state.completeInfiniteRotation()) {
                    TuskAddon.syncNailState(player);
                }
            }
        }

        private static Vec3 horizontalLook(LivingEntity entity) {
            Vec3 look =
                    entity.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            return look.lengthSqr() < 1.0E-6D
                    ? new Vec3(0.0D, 0.0D, 1.0D)
                    : look.normalize();
        }

        private static Vec3 actionLook(
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

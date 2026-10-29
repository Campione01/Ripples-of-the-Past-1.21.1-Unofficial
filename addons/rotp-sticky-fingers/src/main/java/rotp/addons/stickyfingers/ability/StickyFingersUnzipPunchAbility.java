package rotp.addons.stickyfingers.ability;

import javax.annotation.Nullable;

import rotp.core.entityattachment.syncheddata.SynchedDataBuilder;
import rotp.core.entityattachment.syncheddata.SyncedDataHolderExtended;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import rotp.addons.stickyfingers.state.DetachedPartTargeting;
import rotp.addons.stickyfingers.state.DetachedPartType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class StickyFingersUnzipPunchAbility
        extends StandEntityHeavyPunchAbility {
    public StickyFingersUnzipPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        createActionObj = UnzipPunchAction::new;
        // 1.16 heavy finishers shout while sneaking too
        setPlaysVoiceLineOnSneak();
    }

    // 1.16 sticky_fingers_unzip_punch: .shout(BRUNO_FINISHER) from the user
    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, StickyFingersAddon.BRUNO_FINISHER);
        }
    }

    public static final class UnzipPunchAction
            extends StandEntityHeavyPunch
            implements SyncedDataHolderExtended {
        private static final EntityDataAccessor<Integer> TARGET_HIT_PART =
                SynchedEntityData.defineId(
                        UnzipPunchAction.class, EntityDataSerializers.INT);

        public UnzipPunchAction(EntityActionType ability) {
            super(ability);
        }

        // 1.16 StickyFingersUnzipOpponent.onTaskSet: the part of a living
        // task target its user aimed at (the Stand without a user), which
        // the unzip follow-ups work on; a failed input that puts this punch
        // back keeps the part it took
        @Override
        public void onActionSet(EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            if (!level().isClientSide()
                    && !isRestoringInputLifecycle()
                    && performer instanceof StandEntity stand) {
                Entity target = getTaskTarget(level()).getEntity();
                if (target instanceof LivingEntity) {
                    LivingEntity user = getPowerUser();
                    synchedData.set(
                            TARGET_HIT_PART,
                            DetachedPartTargeting.aimedRegion(
                                            target,
                                            user != null ? user : stand)
                                    .ordinal());
                }
            }
        }

        @Nullable
        public DetachedPartType.Region getRecordedPart() {
            int part = synchedData.get(TARGET_HIT_PART);
            DetachedPartType.Region[] parts =
                    DetachedPartType.Region.values();
            return part >= 0 && part < parts.length ? parts[part] : null;
        }

        // 1.16 StickyFingersUnzipOpponent.rotateStandTowardsTarget: the Stand
        // faces the recorded part of its living target
        @Override
        @Nullable
        public Vec3 getStandLookTargetPosition(
                StandEntity standEntity, ActionTarget target) {
            DetachedPartType.Region part = getRecordedPart();
            if (part != null
                    && target.getEntity() instanceof LivingEntity living) {
                return DetachedPartTargeting.partCenter(part, living);
            }
            return super.getStandLookTargetPosition(standEntity, target);
        }

        @Override
        public void defineSynchedData(SynchedDataBuilder builder) {
            builder.define(TARGET_HIT_PART, -1);
        }

        @Override
        public <T> void onSyncedDataUpdated(
                T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}

        @Override
        protected void hitEntity(
                ActionTarget target,
                Level level,
                StandEntity stand,
                DamageSource damageSource,
                float damageAmount,
                float explosionRadius) {
            if (target.getMainEntity()
                    instanceof LivingEntity livingTarget) {
                livingTarget.addEffect(new MobEffectInstance(
                        StickyFingersAddon.ZIPPER_EFFECT,
                        StickyFingersPhase1Spec
                                .ZIPPER_EFFECT_DURATION_TICKS));
            }
            super.hitEntity(
                    target,
                    level,
                    stand,
                    damageSource,
                    damageAmount
                            * StickyFingersPhase1Spec
                                    .UNZIP_BASE_DAMAGE_MULTIPLIER,
                    explosionRadius);
        }

        // 1.16 StickyFingersUnzipOpponent.punchEntity: .multiplyAddKnockback(0)
        @Override
        protected float getAdditionalHeavyPunchKnockback(StandEntity stand) {
            return 0.0F;
        }
    }
}

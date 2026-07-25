package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.customobjects.ObjectEntity;
import com.github.standobyte.jojo.entityattachment.syncheddata.SynchedDataBuilder;
import com.github.standobyte.jojo.entityattachment.syncheddata.SyncedDataHolderExtended;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.subsystems.target.ActionTarget;
import com.github.standobyte.jojoimpl.stands._entitybase.StandAbilityStamina;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityPunchAbility;

import javax.annotation.Nullable;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class GoldExperienceHeavyPunchAbility extends StandEntityHeavyPunchAbility {
    private static final String LIFESHOT_PUNCH_ABILITY = "lifeshot_punch";
    private static final float LIFESHOT_KNOCKBACK_MULTIPLIER = 1.25F;
    private static final float TOOTH_KNOCKOUT_CHANCE = 0.38F;
    private static final int TOOTH_KNOCKOUT_COOLDOWN_TICKS = 5 * 20;
    private static final Map<UUID, Long> LAST_TOOTH_KNOCKOUT_TICKS = new HashMap<>();

    public GoldExperienceHeavyPunchAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        this.createActionObj = ToothKnockingHeavyPunch::new;
    }

    public static class ToothKnockingHeavyPunch extends StandEntityHeavyPunchAbility.StandEntityHeavyPunch implements SyncedDataHolderExtended {
        private static final EntityDataAccessor<Boolean> TOOTH_READY = SynchedEntityData.defineId(
                ToothKnockingHeavyPunch.class, EntityDataSerializers.BOOLEAN);

        private int toothEntityId = -1;
        private int hitTargetId = -1;
        private boolean toothTransformTried;
        @Nullable
        private String queuedToothLifeformId;
        private boolean queuedLifeshot;
        private boolean lifeshotApplied;

        public ToothKnockingHeavyPunch(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            super.actionTick();
            tryApplyQueuedLifeshot();
            tryTransformQueuedTooth(true);
        }

        public boolean canAcceptToothLifeformFollowup() {
            return !isLifeshotPunch()
                    && queuedToothLifeformId == null
                    && !toothTransformTried
                    && isToothReady();
        }

        public boolean queueToothLifeform(String selectedLifeformId) {
            if (!canAcceptToothLifeformFollowup()) {
                return false;
            }
            queuedToothLifeformId = selectedLifeformId;
            setToothReady(false);
            tryTransformQueuedTooth(false);
            return true;
        }

        public boolean canAcceptLifeshotFollowup() {
            return isLifeshotPunch()
                    && !queuedLifeshot
                    && !lifeshotApplied
                    && hasEntityTargetSnapshot();
        }

        public boolean queueLifeshot() {
            if (!canAcceptLifeshotFollowup()) {
                return false;
            }
            queuedLifeshot = true;
            return true;
        }

        @Nullable
        public LivingEntity getLifeshotTarget() {
            if (hitTargetId >= 0) {
                Entity hitTarget = level().getEntity(hitTargetId);
                if (hitTarget instanceof LivingEntity living && living.isAlive()) {
                    return living;
                }
            }
            Entity target = getFreshTarget().getMainEntity();
            return target instanceof LivingEntity living ? living : null;
        }

        private boolean hasEntityTargetSnapshot() {
            return getFreshTarget().getMainEntity() instanceof LivingEntity;
        }

        private ActionTarget getFreshTarget() {
            return getPerformer() instanceof StandEntity stand
                    ? StandEntityPunchAbility.getFreshPunchTarget(stand, getActionTargetSnapshot(level()))
                    : ActionTarget.EMPTY;
        }

        @Override
        protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource,
                float dmgAmount, boolean hurt) {
            super.afterHeavyPunchHit(stand, targetLiving, dmgSource, dmgAmount, hurt);
            if (!hurt || !(stand.level() instanceof ServerLevel serverLevel)) {
                return;
            }

            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }

            LivingEntity targetUser = StandUtil.getStandUser(targetLiving);
            if (targetUser == null) {
                targetUser = targetLiving;
            }

            if (isLifeshotPunch()) {
                hitTargetId = targetLiving.getId();
                tryApplyQueuedLifeshot();
                return;
            }

            if (shouldKnockOutTooth(stand, targetUser)) {
                ObjectEntity tooth = knockOutTooth(serverLevel, stand, targetUser);
                toothEntityId = tooth.getId();
                hitTargetId = targetUser.getId();
                setToothReady(true);
            }
        }

        private boolean tryApplyQueuedLifeshot() {
            if (!queuedLifeshot || lifeshotApplied || !isLifeshotPunch() || hitTargetId < 0 || level().isClientSide()) {
                return false;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower = user != null ? StandPower.get(user) : null;
            LivingEntity target = getLifeshotTarget();
            if (user == null || standPower == null || target == null) {
                return false;
            }
            lifeshotApplied = true;
            return StandAbilityStamina.consumeOrMessage(ability, standPower, user, GoldExperienceEntityLifeshotAbility.STAMINA_COST)
                    && GoldExperienceEntityLifeshotAbility.applyLifeShotTo(target, user);
        }

        private boolean tryTransformQueuedTooth(boolean requireRecoveryEnd) {
            if (queuedToothLifeformId == null
                    || toothEntityId < 0
                    || toothTransformTried
                    || getPhase() != ActionPhase.RECOVERY
                    || requireRecoveryEnd && getPhaseTicksLeft() > 1.0F
                    || level().isClientSide()) {
                return false;
            }

            Level level = level();
            LivingEntity user = getPowerUser();
            StandPower standPower = user != null ? StandPower.get(user) : null;
            if (!(level instanceof ServerLevel serverLevel) || user == null || standPower == null) {
                return false;
            }

            Entity tooth = level.getEntity(toothEntityId);
            Entity hitTarget = level.getEntity(hitTargetId);
            if (tooth instanceof ObjectEntity toothObject && hitTarget instanceof LivingEntity hitLiving) {
                toothTransformTried = true;
                boolean transformed = GoldExperienceToothLifeformAbility.tryTransformToothObject(serverLevel, standPower, user, hitLiving,
                        toothObject, queuedToothLifeformId);
                if (transformed) {
                    setToothReady(false);
                }
                return transformed;
            }
            return false;
        }

        @Override
        protected void addKnockback(DamageSource dmgSource) {
            if (!(performer instanceof StandEntity stand)) {
                super.addKnockback(dmgSource);
                return;
            }
            if (isLifeshotPunch()) {
                float knockback = getAdditionalHeavyPunchKnockback(stand) * LIFESHOT_KNOCKBACK_MULTIPLIER;
                ((DamageSourceModified) dmgSource).jojo_ripples$modifyKnockback(knockback, 1);
            }
            else {
                super.addKnockback(dmgSource);
            }
        }

        private boolean isLifeshotPunch() {
            return ability.getAbilityId() != null && LIFESHOT_PUNCH_ABILITY.equals(ability.getAbilityId().nameInMoveset());
        }

        private boolean isToothReady() {
            return synchedData.get(TOOTH_READY);
        }

        private void setToothReady(boolean ready) {
            synchedData.set(TOOTH_READY, ready);
        }

        private static boolean shouldKnockOutTooth(StandEntity stand, LivingEntity target) {
            long gameTime = stand.level().getGameTime();
            UUID targetId = target.getUUID();
            Long lastKnockoutTick = LAST_TOOTH_KNOCKOUT_TICKS.get(targetId);
            if (lastKnockoutTick != null && gameTime - lastKnockoutTick < TOOTH_KNOCKOUT_COOLDOWN_TICKS) {
                return false;
            }
            if (stand.getRandom().nextFloat() >= TOOTH_KNOCKOUT_CHANCE) {
                return false;
            }
            LAST_TOOTH_KNOCKOUT_TICKS.put(targetId, gameTime);
            LAST_TOOTH_KNOCKOUT_TICKS.entrySet().removeIf(entry -> gameTime - entry.getValue() > 20 * 60);
            return true;
        }

        private static ObjectEntity knockOutTooth(ServerLevel level, StandEntity stand, LivingEntity target) {
            Vec3 mouthPos = target.position().add(0.0D, target.getEyeHeight() * 0.8D, 0.0D);
            ObjectEntity tooth = new ObjectEntity(level, ObjectEntity.Type.TOOTH);
            tooth.setOwner(target.getUUID());
            tooth.setPos(mouthPos.x, mouthPos.y, mouthPos.z);
            float xRot = -37.5F - 15.0F * stand.getRandom().nextFloat();
            float yRot = 90.0F + 30.0F * stand.getRandom().nextFloat();
            Vec3 toothVec = Vec3.directionFromRotation(target.getXRot() + xRot, target.getYRot() + yRot);
            tooth.setDeltaMovement(toothVec.scale(Math.max(stand.getAttackDamage(), 2.0D) * 0.05D));
            level.addFreshEntity(tooth);
            return tooth;
        }

        @Override
        public void defineSynchedData(SynchedDataBuilder builder) {
            builder.define(TOOTH_READY, false);
        }

        @Override
        public <T> void onSyncedDataUpdated(T oldValue, T newValue, EntityDataAccessor<T> dataAccessor) {}
    }
}

package rotp.addons.purplehaze.entity;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.effect.PurpleHazeVirusEffect;
import rotp.addons.purplehaze.particle.PurpleHazeVirusParticleOptions;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PurpleHazeEntity extends StandEntity {
    public static final int MAX_CAPSULES = 6;
    public static final double AURA_RADIUS = 2.0D;
    public static final int AURA_VIRUS_TICKS = 100;
    public static final int MADNESS_BARRAGE_TICKS = 60;
    public static final int MADNESS_REWARD_TICKS = 100;

    private static final Set<String> AUTONOMOUS_ABILITIES = Set.of(
            PurpleHazeAddon.PUNCH_NAME,
            PurpleHazeAddon.MADNESS_BARRAGE_NAME,
            PurpleHazeAddon.VIRAL_PUNCH_NAME);

    private static final EntityDataAccessor<Integer> CAPSULES_COUNT =
            SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> AURA_ACTIVE =
            SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> MADNESS =
            SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean>
            MADNESS_FROM_ABILITY = SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> MAD_TARGET_ID =
            SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            MAD_TARGET_UUID = SynchedEntityData.defineId(
                    PurpleHazeEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    private boolean automationDispatch;
    private int madnessRewardTicks;
    @Nullable
    private UUID rewardedTargetUuid;

    public PurpleHazeEntity(
            EntityType<? extends PurpleHazeEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CAPSULES_COUNT, MAX_CAPSULES);
        builder.define(AURA_ACTIVE, false);
        builder.define(MADNESS, false);
        builder.define(MADNESS_FROM_ABILITY, false);
        builder.define(MAD_TARGET_ID, -1);
        builder.define(MAD_TARGET_UUID, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        LivingEntity user = getUser();
        StandPower power = getUserPower();
        if (user == null
                || !user.isAlive()
                || power == null
                || power.getPowerType()
                        != PurpleHazeAddon.PURPLE_HAZE.get()
                || power.getSummonedStandEntity() != this) {
            clearRuntimeState();
            return;
        }

        tickMadnessStaminaReward(power);
        if (isCloseToUser() && hasAura()) {
            setAuraActive(false);
        }

        if (isMadCauseOfAbility()) {
            setAuraActive(false);
        }
        else if (shouldEnterAutomaticMadness(user, power)) {
            setMadness(true, false);
        }

        if (hasAura()) {
            tickVirusAura(serverLevel, user);
        }
        if (isMad()) {
            tickMadness(serverLevel, user, power);
        }
        else if (getMadTargetUuid() != null) {
            setAutoAttackTarget(null);
        }
    }

    private void tickVirusAura(
            ServerLevel level, LivingEntity user) {
        for (LivingEntity target :
                level.getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(AURA_RADIUS),
                        target -> target != this
                                && target.isAlive()
                                && !target.hasEffect(
                                        PurpleHazeAddon.PH_VIRUS))) {
            PurpleHazeVirusEffect.applyVirusUnattenuated(
                    target, 0, AURA_VIRUS_TICKS);
        }

        PurpleHazeVirusParticleOptions particles =
                new PurpleHazeVirusParticleOptions(
                        getId(), user.getUUID());
        level.sendParticles(
                particles,
                getRandomX(1.0D),
                getRandomY(),
                getRandomZ(1.0D),
                1,
                0.02D,
                0.02D,
                0.02D,
                0.0D);
    }

    private void tickMadness(
            ServerLevel level,
            LivingEntity user,
            StandPower power) {
        setAuraActive(false);
        releaseManualControlForMadness();

        LivingEntity target = resolveMadTarget(level);
        if (target != null && !target.isAlive()) {
            rewardMadnessKill(target);
            target = null;
            setAutoAttackTarget(null);
        }
        if (!isValidMadnessTarget(target, user)) {
            target = acquireMadnessTarget(level, user);
            setAutoAttackTarget(target);
        }

        if (target == null) {
            stopAutonomousAction();
            retract();
            return;
        }

        moveToMadnessTarget(target);
        getStandActionComponent().entityAim.setTarget(
                new ActionTarget(target));

        compensateMadnessBarrageCost(power);
        if (getCurStandAction() != null
                || power.getStaminaRatio() <= 0.25F) {
            return;
        }

        float healthRatio = target.getMaxHealth() > 0.0F
                ? target.getHealth() / target.getMaxHealth()
                : 1.0F;
        if (getFinisherMeter() > 0.7F
                && healthRatio < 0.15F) {
            dispatchConfiguredAction(
                    power,
                    user,
                    target,
                    PurpleHazeAddon.VIRAL_PUNCH_NAME);
        }
        else if (getFinisherMeter() > 0.3F) {
            dispatchConfiguredAction(
                    power,
                    user,
                    target,
                    PurpleHazeAddon.MADNESS_BARRAGE_NAME);
        }
        else {
            dispatchConfiguredAction(
                    power,
                    user,
                    target,
                    PurpleHazeAddon.PUNCH_NAME);
        }
    }

    private void compensateMadnessBarrageCost(
            StandPower power) {
        EntityActionInstance action = getCurStandAction();
        if (action == null
                || action.ability.getAbilityId() == null
                || !PurpleHazeAddon.MADNESS_BARRAGE_NAME.equals(
                        action.ability.getAbilityId()
                                .nameInMoveset())) {
            return;
        }
        power.setStamina(
                Math.min(
                        power.getMaxStamina(),
                        power.getStamina() + 2.0F));
    }

    private boolean dispatchConfiguredAction(
            StandPower power,
            LivingEntity user,
            LivingEntity target,
            String abilityName) {
        Ability configured = power.getAbility(abilityName);
        if (!(configured instanceof EntityActionAbility ability)) {
            return false;
        }

        getStandActionComponent().entityAim.setTarget(
                new ActionTarget(target));
        automationDispatch = true;
        try {
            EntityActionInstance action =
                    ability.initActionOnAbilityUse(
                            level(), user, this, null);
            if (action == null) {
                return false;
            }
            return getStandActionComponent().setAction(
                    action,
                    user,
                    SyncType.TRACKING_AND_SELF) != null;
        }
        finally {
            automationDispatch = false;
        }
    }

    private void moveToMadnessTarget(LivingEntity target) {
        stopRetraction();
        Vec3 standPos = position();
        Vec3 targetPos = target.position();
        Vec3 toTarget = targetPos.subtract(standPos);
        double minDistance =
                target.getBbWidth() + getBbWidth() + 1.0D;
        if (toTarget.lengthSqr() > minDistance * minDistance) {
            setDeltaMovement(
                    toTarget.x / 10.0D,
                    toTarget.y / 16.0D,
                    toTarget.z / 10.0D);
        }
        else {
            setDeltaMovement(Vec3.ZERO);
        }
        lookAt(
                EntityAnchorArgument.Anchor.EYES,
                target.getEyePosition());
    }

    @Nullable
    private LivingEntity acquireMadnessTarget(
            ServerLevel level, LivingEntity user) {
        double maxRange = getMaxRange();
        return level.getEntitiesOfClass(
                        LivingEntity.class,
                        user.getBoundingBox().inflate(maxRange),
                        target -> isValidMadnessTarget(
                                target, user))
                .stream()
                .min(Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    private boolean isValidMadnessTarget(
            @Nullable LivingEntity target,
            LivingEntity user) {
        return target != null
                && target != this
                && target != user
                && target.isAlive()
                && target.level() == level()
                && target.distanceToSqr(user)
                        < getMaxRange() * getMaxRange()
                && !target.isAlliedTo(user)
                && canAttackEntity(target);
    }

    @Nullable
    private LivingEntity resolveMadTarget(ServerLevel level) {
        UUID targetUuid = getMadTargetUuid();
        Entity target = targetUuid != null
                ? level.getEntity(targetUuid)
                : null;
        return target instanceof LivingEntity living
                ? living
                : null;
    }

    private void rewardMadnessKill(LivingEntity target) {
        UUID targetUuid = target.getUUID();
        if (target.getMaxHealth() >= 20.0F
                && !targetUuid.equals(rewardedTargetUuid)) {
            rewardedTargetUuid = targetUuid;
            madnessRewardTicks = MADNESS_REWARD_TICKS;
        }
    }

    private void tickMadnessStaminaReward(StandPower power) {
        if (madnessRewardTicks <= 0) {
            return;
        }
        madnessRewardTicks--;
        power.setStamina(
                Math.min(
                        power.getMaxStamina(),
                        power.getStamina()
                                + Math.max(
                                        0.0F,
                                        power.getStaminaTickGain())));
    }

    private static boolean shouldEnterAutomaticMadness(
            LivingEntity user, StandPower power) {
        int resolve = power.getResolveLevel();
        float healthRatio = user.getMaxHealth() > 0.0F
                ? user.getHealth() / user.getMaxHealth()
                : 0.0F;
        return resolve <= 0
                || resolve == 1 && healthRatio <= 0.5F
                || resolve == 2 && healthRatio <= 0.25F
                || resolve >= 3 && healthRatio <= 0.1F;
    }

    @Override
    public boolean onActionSet(
            @Nullable EntityActionInstance action) {
        if (!level().isClientSide()
                && isMad()
                && action != null
                && !automationDispatch
                && !isMadnessToggle(action)) {
            return true;
        }
        return super.onActionSet(action);
    }

    private static boolean isMadnessToggle(
            EntityActionInstance action) {
        return action.ability.getAbilityId() != null
                && PurpleHazeAddon.MADNESS_NAME.equals(
                        action.ability.getAbilityId()
                                .nameInMoveset());
    }

    @Override
    public void setManuallyControlled(boolean value) {
        if (value && isMad()) {
            return;
        }
        super.setManuallyControlled(value);
    }

    private void releaseManualControlForMadness() {
        LivingEntity user = getUser();
        if (isManuallyControlled()
                || user != null
                        && EntityComponentController.getControlTarget(user) == this) {
            StandEntityManualControlToggle.off(level(), this, false);
        }
    }

    @Override
    public void onUnsummonUserInput() {
        if (!isMad()) {
            super.onUnsummonUserInput();
        }
    }

    @Override
    public boolean isFollowingUser() {
        return !isMad() && super.isFollowingUser();
    }

    // 1.16 060 PurpleHazeEntity.aimWithThisOrUser: a mad Purple Haze aims
    // with its own eyes at the madness target, not with its user's look;
    // without a target it keeps its user's aim.
    @Override
    public boolean aimsWithoutUser() {
        return isMad() && getMadTargetUuid() != null
                || super.aimsWithoutUser();
    }

    public int getCapsuleCount() {
        return entityData.get(CAPSULES_COUNT);
    }

    public boolean useCapsule() {
        if (level().isClientSide() || getCapsuleCount() <= 0) {
            return false;
        }
        entityData.set(
                CAPSULES_COUNT,
                Mth.clamp(
                        getCapsuleCount() - 1,
                        0,
                        MAX_CAPSULES));
        return true;
    }

    public boolean hasAura() {
        return entityData.get(AURA_ACTIVE);
    }

    public void setAuraActive(boolean active) {
        if (!level().isClientSide()) {
            entityData.set(AURA_ACTIVE, active && !isMad());
        }
    }

    public boolean isMad() {
        return entityData.get(MADNESS);
    }

    public boolean isMadCauseOfAbility() {
        return isMad() && entityData.get(MADNESS_FROM_ABILITY);
    }

    public void toggleAbilityMadness() {
        if (level().isClientSide()) {
            return;
        }
        if (isMadCauseOfAbility()) {
            setMadness(false, false);
        }
        else if (!isMad()) {
            setMadness(true, true);
        }
    }

    public void setMadness(
            boolean madness, boolean fromAbility) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(MADNESS, madness);
        entityData.set(
                MADNESS_FROM_ABILITY,
                madness && fromAbility);
        if (madness) {
            setAuraActive(false);
            releaseManualControlForMadness();
        }
        else {
            setAutoAttackTarget(null);
            stopAutonomousAction();
            rewardedTargetUuid = null;
            setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean isAutomationDispatch() {
        return automationDispatch;
    }

    @Nullable
    public UUID getMadTargetUuid() {
        return entityData.get(MAD_TARGET_UUID).orElse(null);
    }

    public int getMadTargetId() {
        return entityData.get(MAD_TARGET_ID);
    }

    public void setAutoAttackTarget(
            @Nullable LivingEntity target) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(
                MAD_TARGET_ID,
                target != null ? target.getId() : -1);
        entityData.set(
                MAD_TARGET_UUID,
                Optional.ofNullable(
                        target != null
                                ? target.getUUID()
                                : null));
        if (target != null
                && !target.getUUID().equals(rewardedTargetUuid)) {
            rewardedTargetUuid = null;
        }
    }

    private void stopAutonomousAction() {
        EntityActionInstance action = getCurStandAction();
        if (action == null
                || action.ability.getAbilityId() == null
                || !AUTONOMOUS_ABILITIES.contains(
                        action.ability.getAbilityId()
                                .nameInMoveset())) {
            return;
        }
        getStandActionComponent().setAction(
                null, SyncType.TRACKING_AND_SELF);
    }

    private void clearRuntimeState() {
        if (level().isClientSide()) {
            return;
        }
        stopAutonomousAction();
        if (getCurStandAction() != null) {
            getStandActionComponent().setAction(
                    null, SyncType.TRACKING_AND_SELF);
        }
        entityData.set(AURA_ACTIVE, false);
        entityData.set(MADNESS, false);
        entityData.set(MADNESS_FROM_ABILITY, false);
        setAutoAttackTarget(null);
        setDeltaMovement(Vec3.ZERO);
        madnessRewardTicks = 0;
        rewardedTargetUuid = null;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        clearRuntimeState();
        super.remove(reason);
    }
}

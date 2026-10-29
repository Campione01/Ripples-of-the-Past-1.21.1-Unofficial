package rotp.addons.thesun.entity;

import java.util.Optional;

import rotp.core.init.ModEntityDataSerializers;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.impl.stands._entitybase.StandEntityUnsummonAction.StandUnsummonInstance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class TheSunEntity extends StandEntity {
    public static final double SUMMON_HEIGHT_ABOVE_USER = 10.0D;
    public static final double TARGET_HEIGHT_ABOVE_SURFACE = 100.0D;
    public static final double ASCENT_SPEED = 1.2D;
    public static final double HOVER_LERP = 0.25D;
    public static final double STABLE_HEIGHT_TOLERANCE = 2.0D;
    public static final double EFFECT_RADIUS = 300.0D;
    public static final double EFFECT_RADIUS_SQR =
            EFFECT_RADIUS * EFFECT_RADIUS;
    public static final int CLIENT_STABLE_FALLBACK_TICKS = 140;

    private static final String NBT_TARGET_X = "TheSunFixedTargetX";
    private static final String NBT_TARGET_Y = "TheSunFixedTargetY";
    private static final String NBT_TARGET_Z = "TheSunFixedTargetZ";
    private static final String NBT_TARGET_SET =
            "TheSunFixedTargetSet";

    private static final EntityDataAccessor<Optional<Vec3>>
            FIXED_TARGET =
            SynchedEntityData.defineId(
                    TheSunEntity.class,
                    ModEntityDataSerializers
                            .VEC3_OPTIONAL.get());
    private static final EntityDataAccessor<Boolean> FIXED_TARGET_SET =
            SynchedEntityData.defineId(
                    TheSunEntity.class,
                    EntityDataSerializers.BOOLEAN);

    public TheSunEntity(
            EntityType<? extends TheSunEntity> type,
            Level level) {
        super(type, level);
        onlyVisibleToStandUsers = false;
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FIXED_TARGET, Optional.empty());
        builder.define(FIXED_TARGET_SET, false);
    }

    @Override
    public void setUserAndPower(
            LivingEntity user, StandPower power) {
        super.setUserAndPower(user, power);
        if (!level().isClientSide()
                && user != null
                && !hasFixedTarget()) {
            setInitialSunPosition(user);
        }
    }

    public void setInitialSunPosition(LivingEntity user) {
        if (level().isClientSide() || user == null) {
            return;
        }
        setFixedSunTarget(user.getX(), user.getZ());
        setPos(
                user.getX(),
                user.getY() + SUMMON_HEIGHT_ABOVE_USER,
                user.getZ());
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);

        if (!level().isClientSide() && !hasFixedTarget()) {
            setFixedSunTarget(getX(), getZ());
        }
        tickSunHoverPosition();
    }

    @Override
    public void updatePosition(LivingEntity user) {
        // H4 world manifestation: the fixed sky anchor never follows the user.
    }

    @Override
    public void onUnsummonUserInput() {
        if (level().isClientSide() || getUser() == null) {
            return;
        }
        // A fixed sky manifestation cannot complete movement-based retraction.
        if (isBeingRetracted()) {
            stopRetraction();
        }
        setCanFollowUser(true);
        if (!(getCurStandAction() instanceof StandUnsummonInstance)) {
            startStandUnsummon();
        }
    }

    public boolean hasFixedTarget() {
        return entityData.get(FIXED_TARGET_SET);
    }

    public boolean isSunStable() {
        if (level().isClientSide()) {
            return tickCount >= CLIENT_STABLE_FALLBACK_TICKS;
        }
        if (hasFixedTarget()) {
            return Math.abs(
                            getY()
                                    - entityData.get(
                                            FIXED_TARGET)
                                            .orElse(Vec3.ZERO)
                                            .y)
                    <= STABLE_HEIGHT_TOLERANCE;
        }
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        Vec3 target = entityData.get(FIXED_TARGET)
                .orElse(Vec3.ZERO);
        compound.putDouble(NBT_TARGET_X, target.x);
        compound.putDouble(NBT_TARGET_Y, target.y);
        compound.putDouble(NBT_TARGET_Z, target.z);
        compound.putBoolean(
                NBT_TARGET_SET, hasFixedTarget());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        entityData.set(
                FIXED_TARGET,
                Optional.of(new Vec3(
                        compound.getDouble(NBT_TARGET_X),
                        compound.getDouble(NBT_TARGET_Y),
                        compound.getDouble(NBT_TARGET_Z))));
        entityData.set(
                FIXED_TARGET_SET,
                compound.getBoolean(NBT_TARGET_SET));
    }

    private void tickSunHoverPosition() {
        if (level().isClientSide() || !hasFixedTarget()) {
            return;
        }
        Vec3 target = entityData.get(FIXED_TARGET)
                .orElse(Vec3.ZERO);
        double nextX = Mth.lerp(
                HOVER_LERP, getX(), target.x);
        double nextZ = Mth.lerp(
                HOVER_LERP, getZ(), target.z);
        double targetY = target.y;
        double yDiff = targetY - getY();
        double nextY = Math.abs(yDiff) <= ASCENT_SPEED
                ? targetY
                : getY() + Math.signum(yDiff) * ASCENT_SPEED;
        setPos(nextX, nextY, nextZ);
        setDeltaMovement(Vec3.ZERO);
    }

    private void setFixedSunTarget(double x, double z) {
        int surfaceY = level().getHeight(
                Heightmap.Types.WORLD_SURFACE,
                Mth.floor(x),
                Mth.floor(z));
        entityData.set(
                FIXED_TARGET,
                Optional.of(new Vec3(
                        x,
                        surfaceY + TARGET_HEIGHT_ABOVE_SURFACE,
                        z)),
                true);
        entityData.set(FIXED_TARGET_SET, true, true);
    }
}

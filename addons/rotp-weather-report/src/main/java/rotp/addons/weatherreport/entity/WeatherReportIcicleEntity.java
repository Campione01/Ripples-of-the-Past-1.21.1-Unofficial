package rotp.addons.weatherreport.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class WeatherReportIcicleEntity
        extends OwnerBoundProjectileEntity {
    public static final int LIFE_SPAN_TICKS = 20;
    public static final float BASE_DAMAGE = 0.5F;
    public static final float MOVEMENT_SPEED = 0.75F;
    public static final int FULL_LENGTH_TICKS = 4;
    public static final float RETRACT_SPEED = 0.5F;
    public static final int BLEEDING_DURATION_TICKS = 200;

    private static final Vec3 OFFSET =
            new Vec3(-0.35D, -0.25D, 0.25D);
    private static final EntityDataAccessor<Boolean> MOVING_FORWARD =
            SynchedEntityData.defineId(
                    WeatherReportIcicleEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(
                    WeatherReportIcicleEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private double distance;

    public WeatherReportIcicleEntity(
            LivingEntity shooter, Level level) {
        super(
                WeatherReportAddon.ICICLE_STRIKE_PROJECTILE.get(),
                shooter,
                level);
    }

    public WeatherReportIcicleEntity(
            EntityType<? extends WeatherReportIcicleEntity> type,
            Level level) {
        super(type, level);
    }

    @Override
    public int ticksLifespan() {
        return LIFE_SPAN_TICKS;
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.0F;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    protected boolean shouldFollowOwner() {
        return false;
    }

    @Override
    public boolean isBodyPart() {
        return true;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return OFFSET;
    }

    public void beginRetracting() {
        entityData.set(MOVING_FORWARD, false);
        entityData.set(RETRACTING, true);
    }

    @Override
    protected void moveProjectile() {
        Entity owner = getOwner();
        if (!(owner instanceof LivingEntity livingOwner)) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        setRot(livingOwner.getYRot(), livingOwner.getXRot());
        Vec3 originPoint = ownerPosition(1.0F, false);
        Vec3 nextOriginOffset = getNextOriginOffset(livingOwner);
        if (nextOriginOffset == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        double x = getX();
        double y = getY();
        double z = getZ();
        double nextX = originPoint.x + nextOriginOffset.x;
        double nextY = originPoint.y + nextOriginOffset.y;
        double nextZ = originPoint.z + nextOriginOffset.z;
        setDeltaMovement(nextX - x, nextY - y, nextZ - z);

        xo = x;
        yo = y;
        zo = z;
        xOld = x;
        yOld = y;
        zOld = z;
        setPos(nextX, nextY, nextZ);
    }

    private Vec3 getNextOriginOffset(LivingEntity owner) {
        double nextDistance = updateDistance();
        updateMotionFlags();
        if (isRetracting() && nextDistance <= 0.0D) {
            return null;
        }
        distance = Math.max(0.0D, nextDistance);
        return Vec3.directionFromRotation(
                owner.getXRot(), owner.getYRot()).scale(distance);
    }

    private double updateDistance() {
        if (isRetracting()) {
            return distance - RETRACT_SPEED * speedFactor;
        }
        if (isMovingForward()) {
            return distance + MOVEMENT_SPEED * speedFactor;
        }
        return distance;
    }

    private void updateMotionFlags() {
        int stopForwardMotionMark = Math.max(
                1, (int) (maxDistance() / MOVEMENT_SPEED));
        if (isMovingForward()
                && tickCount >= stopForwardMotionMark) {
            entityData.set(MOVING_FORWARD, false);
        }
        if (!isRetracting()
                && tickCount
                        >= stopForwardMotionMark + FULL_LENGTH_TICKS) {
            entityData.set(RETRACTING, true);
        }
    }

    private boolean isMovingForward() {
        return entityData.get(MOVING_FORWARD);
    }

    private boolean isRetracting() {
        return entityData.get(RETRACTING);
    }

    private double maxDistance() {
        return MOVEMENT_SPEED * RETRACT_SPEED
                * (LIFE_SPAN_TICKS - FULL_LENGTH_TICKS)
                / (MOVEMENT_SPEED + RETRACT_SPEED);
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        playSound(
                WeatherReportAddon.WEATHER_REPORT_FINISHER_SOUND.get(),
                1.0F,
                1.0F);
        if (target instanceof LivingEntity living
                && JojoDefinitions.canBleed(living)) {
            MobEffectInstance current =
                    living.getEffect(ModStatusEffects.BLEEDING);
            int amplifier =
                    current != null ? current.getAmplifier() + 1 : 0;
            living.addEffect(new MobEffectInstance(
                    ModStatusEffects.BLEEDING,
                    BLEEDING_DURATION_TICKS,
                    amplifier,
                    false,
                    false,
                    true));
        }
        return super.hurtTarget(target, owner);
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hitResult, boolean entityHurt) {
        super.afterEntityHit(hitResult, entityHurt);
        beginRetracting();
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult, boolean blockDestroyed) {
        super.afterBlockHit(hitResult, blockDestroyed);
        playSound(
                WeatherReportAddon.WEATHER_REPORT_FINISHER_SOUND.get(),
                1.0F,
                1.0F);
        beginRetracting();
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        // The legacy body part retracts after impacts instead of breaking.
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level().isClientSide()) {
            Vec3 velocity = getDeltaMovement();
            for (int i = 0; i < 5; i++) {
                level().addParticle(
                        ParticleTypes.CLOUD,
                        getX() - 0.35D,
                        getY() - 0.3D,
                        getZ() + 0.25D,
                        velocity.x,
                        velocity.y,
                        velocity.z);
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide()
                && getOwner()
                        instanceof WeatherReportStandEntity stand) {
            stand.clearActiveIcicle(getId());
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeDouble(distance);
        buffer.writeBoolean(isMovingForward());
        buffer.writeBoolean(isRetracting());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        distance = buffer.readDouble();
        entityData.set(MOVING_FORWARD, buffer.readBoolean());
        entityData.set(RETRACTING, buffer.readBoolean());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Distance", distance);
        tag.putBoolean("MovingForward", isMovingForward());
        tag.putBoolean("Retracting", isRetracting());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        distance = tag.getDouble("Distance");
        entityData.set(
                MOVING_FORWARD,
                !tag.contains("MovingForward")
                        || tag.getBoolean("MovingForward"));
        entityData.set(
                RETRACTING, tag.getBoolean("Retracting"));
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOVING_FORWARD, true);
        builder.define(RETRACTING, false);
    }
}

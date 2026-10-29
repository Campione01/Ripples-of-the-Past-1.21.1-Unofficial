package rotp.addons.planetwaves.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import rotp.core.powersystem.standpower.entity.StandEntity;

public final class MeteoriteEntity extends Entity {
    public static final float LEGACY_SIZE = 6.0F;
    public static final double SPEED_PER_TICK = 1.0D;
    public static final float EXPLOSION_POWER = 4.0F;
    public static final double ARRIVAL_DISTANCE_SQR = 12.25D;
    public static final double LEGACY_TARGET_PULSE_DISTANCE_SQR =
            1600.0D;
    public static final int MAX_LIFETIME_TICKS = 600;
    public static final int ORPHAN_GRACE_TICKS = 40;

    private static final String CONTROLLER_TAG = "Controller";
    private static final String TARGET_TAG = "Target";
    private static final String SIZE_TAG = "Size";
    private static final String VERTICAL_TAG = "VerticalAttraction";

    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLER_UUID = SynchedEntityData.defineId(
                    MeteoriteEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer>
            CONTROLLER_ENTITY_ID = SynchedEntityData.defineId(
                    MeteoriteEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>>
            TARGET_UUID = SynchedEntityData.defineId(
                    MeteoriteEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(
                    MeteoriteEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean>
            VERTICAL_ATTRACTION = SynchedEntityData.defineId(
                    MeteoriteEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int orphanTicks;

    public MeteoriteEntity(
            EntityType<? extends MeteoriteEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
        setRemainingFireTicks(Integer.MAX_VALUE);
    }

    public void initialize(
            StandEntity controller,
            LivingEntity target,
            boolean verticalAttraction,
            float size) {
        entityData.set(
                CONTROLLER_UUID,
                Optional.of(controller.getUUID()));
        entityData.set(
                CONTROLLER_ENTITY_ID,
                controller.getId());
        entityData.set(
                TARGET_UUID,
                Optional.of(target.getUUID()));
        entityData.set(VERTICAL_ATTRACTION, verticalAttraction);
        entityData.set(SIZE, size);
    }

    public float getMeteorSize() {
        return entityData.get(SIZE);
    }

    public boolean isVerticalAttraction() {
        return entityData.get(VERTICAL_ATTRACTION);
    }

    @Nullable
    public StandEntity resolveController() {
        int entityId = entityData.get(CONTROLLER_ENTITY_ID);
        Entity byId =
                entityId >= 0 ? level().getEntity(entityId) : null;
        if (byId instanceof StandEntity stand) {
            return stand;
        }
        UUID uuid = entityData.get(CONTROLLER_UUID).orElse(null);
        if (uuid != null && level() instanceof ServerLevel serverLevel) {
            Entity byUuid = serverLevel.getEntity(uuid);
            if (byUuid instanceof StandEntity stand) {
                entityData.set(CONTROLLER_ENTITY_ID, stand.getId());
                return stand;
            }
        }
        return null;
    }

    @Nullable
    private LivingEntity resolveTarget(ServerLevel level) {
        UUID uuid = entityData.get(TARGET_UUID).orElse(null);
        Entity entity = uuid != null ? level.getEntity(uuid) : null;
        return entity instanceof LivingEntity living
                && living.isAlive()
                        ? living
                        : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (tickCount >= MAX_LIFETIME_TICKS) {
            discard();
            return;
        }
        if (!serverLevel.getBlockState(blockPosition()).isAir()) {
            explode(serverLevel);
            return;
        }

        LivingEntity target = resolveTarget(serverLevel);
        StandEntity controller = resolveController();
        if (target == null || controller == null) {
            if (++orphanTicks >= ORPHAN_GRACE_TICKS) {
                discard();
            }
            return;
        }
        orphanTicks = 0;

        if (distanceToSqr(target) < ARRIVAL_DISTANCE_SQR) {
            harmlessDespawn(serverLevel);
            return;
        }

        Vec3 movement = attractionVector(target);
        Vec3 next = position().add(movement);
        if (!serverLevel.getBlockState(
                BlockPos.containing(next)).isAir()) {
            explode(serverLevel);
            return;
        }

        AABB swept = getBoundingBox()
                .expandTowards(movement)
                .inflate(1.0D);
        if (!serverLevel.getEntities(
                this,
                swept,
                entity -> entity.isAlive()
                        && entity != controller).isEmpty()) {
            explode(serverLevel);
            return;
        }

        setDeltaMovement(movement);
        move(MoverType.SELF, movement);
        if (horizontalCollision || verticalCollision) {
            explode(serverLevel);
            return;
        }
        updateRotation(movement);
        emitTrail(serverLevel);
    }

    private Vec3 attractionVector(LivingEntity target) {
        double targetY = target.getY() + 1.0D;
        if (isVerticalAttraction()
                && getY() > targetY + 6.0D) {
            return new Vec3(0.0D, -SPEED_PER_TICK, 0.0D);
        }
        Vec3 delta = new Vec3(
                target.getX() - getX(),
                (isVerticalAttraction()
                        ? targetY
                        : target.getY()) - getY(),
                target.getZ() - getZ());
        return delta.lengthSqr() > 1.0E-7D
                ? delta.normalize().scale(SPEED_PER_TICK)
                : Vec3.ZERO;
    }

    private void updateRotation(Vec3 movement) {
        if (movement.lengthSqr() < 1.0E-7D) {
            return;
        }
        setYRot((float) (
                Mth.atan2(movement.x, movement.z)
                        * Mth.RAD_TO_DEG));
        setXRot((float) (
                Mth.atan2(
                        movement.y,
                        movement.horizontalDistance())
                        * Mth.RAD_TO_DEG));
    }

    private void explode(ServerLevel level) {
        LivingEntity target = resolveTarget(level);
        level.explode(
                null,
                getX(),
                getY(),
                getZ(),
                EXPLOSION_POWER,
                false,
                Level.ExplosionInteraction.TNT);
        if (target != null
                && distanceToSqr(target)
                        < LEGACY_TARGET_PULSE_DISTANCE_SQR) {
            target.hurt(
                    level.damageSources().explosion(null, null),
                    0.0F);
        }
        discard();
    }

    private void harmlessDespawn(ServerLevel level) {
        level.sendParticles(
                ParticleTypes.POOF,
                getX(), getY(), getZ(),
                15, 0.3D, 0.3D, 0.3D, 0.05D);
        level.sendParticles(
                ParticleTypes.LAVA,
                getX(), getY(), getZ(),
                10, 0.2D, 0.2D, 0.2D, 0.01D);
        level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                getX(), getY(), getZ(),
                10, 0.2D, 0.2D, 0.2D, 0.01D);
        level.playSound(
                null,
                getX(), getY(), getZ(),
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.NEUTRAL,
                1.0F,
                1.0F);
        level.playSound(
                null,
                getX(), getY(), getZ(),
                SoundEvents.GENERIC_EXPLODE,
                SoundSource.NEUTRAL,
                1.0F,
                1.0F);
        discard();
    }

    private void emitTrail(ServerLevel level) {
        level.sendParticles(
                ParticleTypes.FLAME,
                getX(), getY(), getZ(),
                5, 0.5D, 0.5D, 0.5D, 0.01D);
        level.sendParticles(
                ParticleTypes.SMOKE,
                getX(), getY(), getZ(),
                10, 0.4D, 0.4D, 0.4D, 0.02D);
        level.sendParticles(
                ParticleTypes.LAVA,
                getX(), getY(), getZ(),
                5, 0.3D, 0.3D, 0.3D, 0.05D);
        level.sendParticles(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                getX(), getY(), getZ(),
                5, 0.4D, 0.4D, 0.4D, 0.01D);
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(CONTROLLER_UUID, Optional.empty());
        builder.define(CONTROLLER_ENTITY_ID, -1);
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(SIZE, LEGACY_SIZE);
        builder.define(VERTICAL_ATTRACTION, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(CONTROLLER_UUID)
                .ifPresent(uuid -> tag.putUUID(
                        CONTROLLER_TAG, uuid));
        entityData.get(TARGET_UUID)
                .ifPresent(uuid -> tag.putUUID(TARGET_TAG, uuid));
        tag.putFloat(SIZE_TAG, getMeteorSize());
        tag.putBoolean(VERTICAL_TAG, isVerticalAttraction());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                CONTROLLER_UUID,
                tag.hasUUID(CONTROLLER_TAG)
                        ? Optional.of(tag.getUUID(CONTROLLER_TAG))
                        : Optional.empty());
        entityData.set(CONTROLLER_ENTITY_ID, -1);
        entityData.set(
                TARGET_UUID,
                tag.hasUUID(TARGET_TAG)
                        ? Optional.of(tag.getUUID(TARGET_TAG))
                        : Optional.empty());
        entityData.set(
                SIZE,
                tag.contains(SIZE_TAG)
                        ? tag.getFloat(SIZE_TAG)
                        : LEGACY_SIZE);
        entityData.set(
                VERTICAL_ATTRACTION,
                tag.getBoolean(VERTICAL_TAG));
    }
}

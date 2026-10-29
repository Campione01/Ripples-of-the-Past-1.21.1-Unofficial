package rotp.addons.purplehaze.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.purplehaze.PurpleHazeAddon;
import rotp.addons.purplehaze.effect.PurpleHazeVirusEffect;
import rotp.addons.purplehaze.particle.PurpleHazeVirusParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class PurpleHazeCloudEntity extends Entity {
    public static final int DAY_LIFETIME_TICKS = 200;
    public static final int NIGHT_LIFETIME_TICKS = 400;
    public static final float DEFAULT_SPREAD_SPEED = 1.0F;

    private static final EntityDataAccessor<Integer> AGE =
            SynchedEntityData.defineId(
                    PurpleHazeCloudEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPREAD_SPEED =
            SynchedEntityData.defineId(
                    PurpleHazeCloudEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CONTROLLER_ID =
            SynchedEntityData.defineId(
                    PurpleHazeCloudEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    PurpleHazeCloudEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public PurpleHazeCloudEntity(
            EntityType<? extends PurpleHazeCloudEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static PurpleHazeCloudEntity spawn(
            ServerLevel level,
            LivingEntity owner,
            @Nullable PurpleHazeEntity controller,
            Vec3 position,
            float spreadSpeed) {
        PurpleHazeCloudEntity cloud =
                new PurpleHazeCloudEntity(
                        PurpleHazeAddon.PURPLE_HAZE_CLOUD.get(),
                        level);
        cloud.setPos(position);
        cloud.entityData.set(
                OWNER_UUID,
                Optional.of(owner.getUUID()));
        cloud.entityData.set(
                CONTROLLER_ID,
                controller != null ? controller.getId() : -1);
        cloud.entityData.set(
                SPREAD_SPEED,
                Math.max(0.05F, spreadSpeed));
        level.addFreshEntity(cloud);
        return cloud;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int age = getAgeTicks() + 1;
        entityData.set(AGE, age);
        refreshControllerId(serverLevel);

        double radius = getRadius();
        for (LivingEntity target :
                serverLevel.getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(radius),
                        target -> !(target instanceof StandEntity)
                                && target.isAlive())) {
            PurpleHazeVirusEffect.applyVirus(
                    target,
                    Math.round(age / 80.0F),
                    age / 2);
        }

        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            double horizontal =
                    radius
                            * (random.nextDouble()
                                    - random.nextDouble());
            double vertical = random.nextDouble() * radius;
            PurpleHazeVirusParticleOptions particles =
                    new PurpleHazeVirusParticleOptions(
                            getControllerId(),
                            ownerUuid);
            serverLevel.sendParticles(
                    particles,
                    getX() + horizontal,
                    getY() + vertical,
                    getZ() + radius
                            * (random.nextDouble()
                                    - random.nextDouble()),
                    8,
                    0.12D,
                    0.12D,
                    0.12D,
                    0.0D);
        }

        int lifetime = serverLevel.isDay()
                ? DAY_LIFETIME_TICKS
                : NIGHT_LIFETIME_TICKS;
        if (age >= lifetime) {
            discard();
        }
    }

    private void refreshControllerId(ServerLevel level) {
        UUID ownerUuid = getOwnerUuid();
        Entity owner = ownerUuid != null
                ? level.getEntity(ownerUuid)
                : null;
        if (owner instanceof LivingEntity living) {
            PurpleHazeEntity controller =
                    PurpleHazeAddon.controller(living);
            entityData.set(
                    CONTROLLER_ID,
                    controller != null ? controller.getId() : -1);
        }
    }

    public int getAgeTicks() {
        return entityData.get(AGE);
    }

    public float getSpreadSpeed() {
        return entityData.get(SPREAD_SPEED);
    }

    public double getRadius() {
        return getAgeTicks() / 20.0D * getSpreadSpeed();
    }

    public int getControllerId() {
        return entityData.get(CONTROLLER_ID);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(AGE, 0);
        builder.define(SPREAD_SPEED, DEFAULT_SPREAD_SPEED);
        builder.define(CONTROLLER_ID, -1);
        builder.define(OWNER_UUID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", getAgeTicks());
        tag.putFloat("SpreadSpeed", getSpreadSpeed());
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(AGE, Math.max(0, tag.getInt("Age")));
        entityData.set(
                SPREAD_SPEED,
                Math.max(
                        0.05F,
                        tag.contains("SpreadSpeed")
                                ? tag.getFloat("SpreadSpeed")
                                : DEFAULT_SPREAD_SPEED));
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(CONTROLLER_ID, -1);
    }
}

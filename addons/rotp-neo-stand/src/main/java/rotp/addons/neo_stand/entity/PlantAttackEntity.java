package rotp.addons.neo_stand.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.addons.neo_stand.NeoStandAddon;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class PlantAttackEntity extends Entity {
    public static final double DEFAULT_LENGTH = 15.0D;
    public static final float DEFAULT_WIDTH = 2.0F;
    public static final int DEFAULT_LIFETIME = 100;

    private static final EntityDataAccessor<Float> FORWARD_X =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FORWARD_Y =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FORWARD_Z =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER_ID =
            SynchedEntityData.defineId(
                    PlantAttackEntity.class,
                    EntityDataSerializers.INT);

    private final Set<Integer> hitEntities = new HashSet<>();
    private final Map<Integer, Integer> lastDamageTick =
            new HashMap<>();
    private UUID ownerUuid;

    public PlantAttackEntity(
            EntityType<? extends PlantAttackEntity> entityType,
            Level level) {
        super(entityType, level);
        noPhysics = true;
        setInvisible(true);
    }

    public PlantAttackEntity(
            Level level,
            Vec3 origin,
            Vec3 forward,
            double length,
            float width,
            int lifetime,
            int ownerId) {
        this(NeoStandAddon.PLANT_ATTACK_ENTITY.get(), level);
        setPos(origin);
        setForward(forward);
        entityData.set(LENGTH, (float) length);
        entityData.set(WIDTH, width);
        entityData.set(LIFETIME, lifetime);
        entityData.set(OWNER_ID, ownerId);
        Entity owner = level.getEntity(ownerId);
        ownerUuid = owner != null ? owner.getUUID() : null;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(FORWARD_X, 0.0F);
        builder.define(FORWARD_Y, 0.0F);
        builder.define(FORWARD_Z, 1.0F);
        builder.define(LENGTH, (float) DEFAULT_LENGTH);
        builder.define(WIDTH, DEFAULT_WIDTH);
        builder.define(LIFETIME, DEFAULT_LIFETIME);
        builder.define(OWNER_ID, -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        // Old ID-only saves cannot safely identify their original caster.
        if (ownerUuid == null) {
            discard();
            return;
        }
        Entity owner = ((ServerLevel) level()).getEntity(ownerUuid);
        entityData.set(OWNER_ID, owner != null ? owner.getId() : -1);

        pinAndDamageEntities();
        int life = entityData.get(LIFETIME);
        entityData.set(LIFETIME, life - 1);
        if (life <= 0) {
            spawnExpiryParticles();
            discard();
        }
    }

    private void pinAndDamageEntities() {
        Vec3 forward = getForward();
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side = forward.cross(up);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            side = side.normalize();
        }
        Vec3 vertical = side.cross(forward).normalize();

        double halfWidth = getWidth() * 0.5D;
        double verticalHalfWidth = 1.5D;
        double length = getLength();
        double horizontalInflation =
                Math.max(length + halfWidth, 3.0D);
        double verticalInflation = Math.max(
                verticalHalfWidth,
                Math.abs(forward.y) * length
                        + verticalHalfWidth);
        AABB search = getBoundingBox().inflate(
                horizontalInflation,
                verticalInflation,
                horizontalInflation);

        for (LivingEntity target :
                level().getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        LivingEntity::isAlive)) {
            if (target.getUUID().equals(ownerUuid)) {
                continue;
            }
            Vec3 relative = target.position()
                    .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                    .subtract(position());
            double along = relative.dot(forward);
            if (along < 0.0D || along > length
                    || Math.abs(relative.dot(side)) > halfWidth
                    || Math.abs(relative.dot(vertical))
                            > verticalHalfWidth) {
                continue;
            }

            target.setDeltaMovement(Vec3.ZERO);
            target.fallDistance = 0.0F;
            int targetId = target.getId();
            if (hitEntities.add(targetId)) {
                target.hurt(damageSources().magic(), 10.0F);
                lastDamageTick.put(targetId, tickCount);
            } else if (tickCount
                    - lastDamageTick.getOrDefault(targetId, 0)
                    >= 20) {
                target.hurt(damageSources().magic(), 1.0F);
                lastDamageTick.put(targetId, tickCount);
            }
        }
    }

    private void spawnExpiryParticles() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        int segments =
                Math.max(1, (int) Math.round(getLength()));
        Vec3 forward = getForward();
        BlockParticleOption particle =
                new BlockParticleOption(
                        ParticleTypes.BLOCK,
                        Blocks.DEAD_BUSH.defaultBlockState());
        for (int segment = 0; segment < segments; segment++) {
            Vec3 position = position().add(
                    forward.scale(segment));
            serverLevel.sendParticles(
                    particle,
                    position.x,
                    position.y,
                    position.z,
                    6,
                    0.1D,
                    0.1D,
                    0.1D,
                    0.08D);
        }
    }

    private void setForward(Vec3 forward) {
        Vec3 normalized = forward.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : forward.normalize();
        entityData.set(FORWARD_X, (float) normalized.x);
        entityData.set(FORWARD_Y, (float) normalized.y);
        entityData.set(FORWARD_Z, (float) normalized.z);
    }

    public Vec3 getForward() {
        return new Vec3(
                entityData.get(FORWARD_X),
                entityData.get(FORWARD_Y),
                entityData.get(FORWARD_Z)).normalize();
    }

    public double getLength() {
        return entityData.get(LENGTH);
    }

    public float getWidth() {
        return entityData.get(WIDTH);
    }

    public int getLifetime() {
        return entityData.get(LIFETIME);
    }

    public int getOwnerId() {
        return entityData.get(OWNER_ID);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setPos(
                tag.getDouble("ox"),
                tag.getDouble("oy"),
                tag.getDouble("oz"));
        setForward(new Vec3(
                tag.getDouble("fx"),
                tag.getDouble("fy"),
                tag.getDouble("fz")));
        entityData.set(LENGTH, tag.getFloat("len"));
        entityData.set(WIDTH, tag.getFloat("w"));
        entityData.set(LIFETIME, tag.getInt("life"));
        ownerUuid = tag.hasUUID("owner_uuid")
                ? tag.getUUID("owner_uuid") : null;
        entityData.set(OWNER_ID, -1);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        Vec3 forward = getForward();
        tag.putDouble("ox", getX());
        tag.putDouble("oy", getY());
        tag.putDouble("oz", getZ());
        tag.putDouble("fx", forward.x);
        tag.putDouble("fy", forward.y);
        tag.putDouble("fz", forward.z);
        tag.putFloat("len", (float) getLength());
        tag.putFloat("w", getWidth());
        tag.putInt("life", getLifetime());
        tag.putInt("owner", getOwnerId());
        if (ownerUuid != null) {
            tag.putUUID("owner_uuid", ownerUuid);
        }
    }
}

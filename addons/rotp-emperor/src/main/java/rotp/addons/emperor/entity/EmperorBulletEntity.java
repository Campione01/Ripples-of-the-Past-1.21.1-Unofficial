package rotp.addons.emperor.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.state.EmperorFireRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EmperorBulletEntity extends ModdedProjectileEntity {
    public static final float NORMAL_DAMAGE =
            EmperorFireRules.NORMAL_DAMAGE;
    public static final float BURST_DAMAGE =
            EmperorFireRules.BURST_DAMAGE;
    public static final float RESOLVE_BURST_DAMAGE =
            EmperorFireRules.RESOLVE_BURST_DAMAGE;
    public static final float MAX_HARDNESS = 0.3F;
    public static final int LIFESPAN_TICKS = 100;
    public static final double MAX_OWNER_DISTANCE = 30.0D;
    public static final double FULL_DAMAGE_DISTANCE = 12.0D;
    public static final int MAX_TRACE_POINTS = 64;

    private static final EntityDataAccessor<Integer> TARGET_ENTITY_ID =
            SynchedEntityData.defineId(
                    EmperorBulletEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    EmperorBulletEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> BASE_DAMAGE =
            SynchedEntityData.defineId(
                    EmperorBulletEntity.class,
                    EntityDataSerializers.FLOAT);

    public final List<Vec3> tracePos = new LinkedList<>();
    public Vec3 initialPos;
    private boolean blockDestroyed;

    public EmperorBulletEntity(
            EntityType<? extends EmperorBulletEntity> entityType,
            Level level) {
        super(entityType, level);
        setNoGravity(true);
    }

    public EmperorBulletEntity(
            LivingEntity shooter, Level level) {
        super(EmperorAddon.EMPEROR_BULLET_ENTITY.get(), shooter, level);
        setNoGravity(true);
    }

    public void setBaseDamage(float damage) {
        entityData.set(BASE_DAMAGE, damage);
    }

    public float baseDamage() {
        return entityData.get(BASE_DAMAGE);
    }

    public void setTargetEntity(LivingEntity target) {
        if (target == null) {
            entityData.set(TARGET_ENTITY_ID, 0);
            entityData.set(TARGET_UUID, Optional.empty());
            return;
        }
        entityData.set(TARGET_ENTITY_ID, target.getId());
        entityData.set(TARGET_UUID, Optional.of(target.getUUID()));
    }

    public LivingEntity getTargetEntity() {
        Entity target;
        if (level() instanceof ServerLevel serverLevel) {
            Optional<UUID> targetUuid = entityData.get(TARGET_UUID);
            target = targetUuid.isPresent()
                    ? serverLevel.getEntity(targetUuid.get())
                    : null;
        } else {
            int targetId = entityData.get(TARGET_ENTITY_ID);
            target = targetId > 0 ? level().getEntity(targetId) : null;
        }
        return target instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        setNoGravity(true);

        LivingEntity owner = getOwner();
        if (!level().isClientSide()
                && owner != null
                && distanceToSqr(owner)
                        > MAX_OWNER_DISTANCE * MAX_OWNER_DISTANCE) {
            discard();
            return;
        }

        if (!level().isClientSide()) {
            LivingEntity target = getTargetEntity();
            if (entityData.get(TARGET_UUID).isPresent()) {
                if (target == null) {
                    // An unloaded target must remain resolvable by UUID.
                    entityData.set(TARGET_ENTITY_ID, 0);
                } else if (!target.isAlive() || target.level() != level()) {
                    setTargetEntity(null);
                } else {
                    entityData.set(TARGET_ENTITY_ID, target.getId());
                }
            }
        } else if (!isRemoved()) {
            Vec3 pos = position();
            boolean add = tracePos.isEmpty()
                    || pos.distanceToSqr(
                            tracePos.get(tracePos.size() - 1)) >= 0.05D;
            if (add) {
                tracePos.add(pos);
                while (tracePos.size() > MAX_TRACE_POINTS) {
                    tracePos.remove(0);
                }
            }
        }
    }

    @Override
    protected void moveProjectile() {
        super.moveProjectile();
        LivingEntity target = getTargetEntity();
        if (target == null
                || !target.isAlive()
                || entityData.get(IS_DEFLECTED)) {
            return;
        }
        Vec3 toTarget = target.getBoundingBox().getCenter()
                .subtract(position());
        double speed = getDeltaMovement().length();
        if (speed > 1.0E-7D && toTarget.lengthSqr() > 1.0E-7D) {
            setDeltaMovement(toTarget.normalize().scale(speed));
        }
    }

    @Override
    protected float getBaseDamage() {
        LivingEntity owner = getOwner();
        float damage = baseDamage();
        if (owner == null) {
            return damage;
        }
        float distance = distanceTo(owner);
        float distanceAdjusted = distance <= FULL_DAMAGE_DISTANCE
                ? damage
                : damage + 6.0F - distance / 2.0F;
        return Math.max(0.0F, distanceAdjusted);
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected boolean constVelocity() {
        return true;
    }

    @Override
    protected boolean hasGravity() {
        return false;
    }

    @Override
    protected double getGravityAcceleration() {
        return 0.0D;
    }

    @Override
    public boolean hasDeflectedVisuals() {
        return true;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult blockHitResult, boolean blockDestroyed) {
        this.blockDestroyed = blockDestroyed;
        if (blockDestroyed) {
            if (!level().isClientSide()) {
                setDeltaMovement(getDeltaMovement().scale(0.9D));
                checkHit();
            }
            return;
        }
        if (!level().isClientSide()) {
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = level().getBlockState(blockPos);
            SoundType soundType =
                    blockState.getSoundType(level(), blockPos, this);
            level().playSound(
                    null,
                    blockPos,
                    soundType.getHitSound(),
                    SoundSource.BLOCKS,
                    1.0F,
                    soundType.getPitch() * 0.5F);
        }
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (targetType == TargetType.BLOCK && blockDestroyed) {
            return;
        }
        super.breakProjectile(targetType, hitTarget);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET_ENTITY_ID, 0);
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(BASE_DAMAGE, NORMAL_DAMAGE);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeInt(entityData.get(TARGET_ENTITY_ID));
        Optional<UUID> targetUuid = entityData.get(TARGET_UUID);
        buffer.writeBoolean(targetUuid.isPresent());
        targetUuid.ifPresent(buffer::writeUUID);
        buffer.writeFloat(baseDamage());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        entityData.set(TARGET_ENTITY_ID, buffer.readInt());
        entityData.set(
                TARGET_UUID,
                buffer.readBoolean()
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty());
        entityData.set(BASE_DAMAGE, buffer.readFloat());
        initialPos = position();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(TARGET_UUID)
                .ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putFloat("BaseDamage", baseDamage());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TARGET_ENTITY_ID, 0);
        entityData.set(
                TARGET_UUID,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
        entityData.set(
                BASE_DAMAGE,
                tag.contains("BaseDamage")
                        ? tag.getFloat("BaseDamage")
                        : NORMAL_DAMAGE);
    }

    @Override
    public void remove(RemovalReason reason) {
        tracePos.clear();
        initialPos = null;
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}

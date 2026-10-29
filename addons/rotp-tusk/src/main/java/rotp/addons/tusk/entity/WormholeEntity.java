package rotp.addons.tusk.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.util.functions.JojoModUtil;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskActRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class WormholeEntity extends Entity {
    public static final float DAMAGE = 1.0F;
    public static final double HOMING_SPEED = 0.15D;
    public static final double DAMAGE_DISTANCE_SQR = 0.2D;
    public static final int LIFETIME_PER_SPIN_CHARGE =
            TuskActRules.LIFETIME_PER_SPIN_CHARGE;

    private static final String OWNER_TAG = "Owner";
    private static final String TARGET_TAG = "Target";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String CREATED_AT_TAG = "CreatedAt";

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    WormholeEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    WormholeEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    WormholeEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> CREATED_AT =
            SynchedEntityData.defineId(
                    WormholeEntity.class,
                    EntityDataSerializers.LONG);

    public WormholeEntity(
            EntityType<? extends WormholeEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public WormholeEntity(
            LivingEntity owner,
            Level level,
            int lifetimeTicks,
            LivingEntity target) {
        this(TuskAddon.WORMHOLE_ENTITY.get(), level);
        setOwnerUuid(owner.getUUID());
        setTargetUuid(target != null ? target.getUUID() : null);
        setCreatedAt(level.getGameTime());
        setExpiresAt(level.getGameTime()
                + Math.max(0, lifetimeTicks));
    }

    public static int lifetimeForSpinCharge(int spinCharge) {
        return TuskActRules.wormholeLifetimeForSpinCharge(
                spinCharge);
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public UUID getTargetUuid() {
        return entityData.get(TARGET_UUID).orElse(null);
    }

    public long getExpiresAt() {
        return entityData.get(EXPIRES_AT);
    }

    public long getCreatedAt() {
        return entityData.get(CREATED_AT);
    }

    public void setOwnerUuid(UUID uuid) {
        entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public void setTargetUuid(UUID uuid) {
        entityData.set(TARGET_UUID, Optional.ofNullable(uuid));
    }

    public void setExpiresAt(long gameTime) {
        entityData.set(EXPIRES_AT, gameTime);
    }

    public void setCreatedAt(long gameTime) {
        entityData.set(CREATED_AT, gameTime);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() >= getExpiresAt()) {
            discard();
            return;
        }

        ServerPlayer owner = resolveOwner(serverLevel);
        if (owner == null
                || !owner.isAlive()
                || !TuskAddon.isTuskUser(owner)) {
            discard();
            return;
        }

        // 1.16 AbstractWormholeEntity.tick: a 0.7 step up after a vertical collision, then the motion plus a 0.7 sink.
        if (verticalCollision) {
            move(
                    MoverType.SELF,
                    getDeltaMovement().add(
                            0.0D, TuskOperationEntity.SINK_PER_TICK, 0.0D));
        }
        move(
                MoverType.SELF,
                getDeltaMovement().add(
                        0.0D, -TuskOperationEntity.SINK_PER_TICK, 0.0D));

        UUID targetUuid = getTargetUuid();
        if (targetUuid == null) {
            return;
        }
        Entity targetEntity = serverLevel.getEntity(targetUuid);
        if (!(targetEntity instanceof LivingEntity target)
                || !target.isAlive()
                || target == owner
                || !JojoModUtil.canHarm(owner, target)) {
            discard();
            return;
        }

        // 1.16 WormholeEntity.tick: the whole height and 0.15 of the horizontal way a tick, then the distance test.
        Vec3 toTarget = target.position().subtract(position());
        move(
                MoverType.SELF,
                getDeltaMovement().add(
                        toTarget.multiply(HOMING_SPEED, 1.0D, HOMING_SPEED)));
        if (target.distanceToSqr(this) < DAMAGE_DISTANCE_SQR) {
            DamageSource source =
                    serverLevel.damageSources().playerAttack(owner);
            target.hurt(source, DAMAGE);
        }
    }

    private ServerPlayer resolveOwner(ServerLevel serverLevel) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid == null) {
            return null;
        }
        ServerPlayer owner = serverLevel.getServer()
                .getPlayerList()
                .getPlayer(ownerUuid);
        return owner != null && owner.level() == serverLevel
                ? owner
                : null;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(EXPIRES_AT, 0L);
        builder.define(CREATED_AT, 0L);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID ownerUuid = getOwnerUuid();
        if (ownerUuid != null) {
            tag.putUUID(OWNER_TAG, ownerUuid);
        }
        UUID targetUuid = getTargetUuid();
        if (targetUuid != null) {
            tag.putUUID(TARGET_TAG, targetUuid);
        }
        tag.putLong(EXPIRES_AT_TAG, getExpiresAt());
        tag.putLong(CREATED_AT_TAG, getCreatedAt());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOwnerUuid(tag.hasUUID(OWNER_TAG)
                ? tag.getUUID(OWNER_TAG)
                : null);
        setTargetUuid(tag.hasUUID(TARGET_TAG)
                ? tag.getUUID(TARGET_TAG)
                : null);
        setExpiresAt(tag.getLong(EXPIRES_AT_TAG));
        setCreatedAt(tag.getLong(CREATED_AT_TAG));
    }
}

package rotp.addons.kiss.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kiss.service.KissService;
import rotp.addons.kiss.service.KissTokenService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KissBombEntity extends Entity {
    public static final int LIFETIME_TICKS = 60;
    public static final double SPEED = 1.8D;
    public static final double ARRIVAL_DISTANCE = 0.5D;
    public static final double DAMAGE_RADIUS = 3.0D;
    public static final float DAMAGE = 15.0F;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> STAND_UUID =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos> TARGET_BLOCK =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> BLOCK_TARGET =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> TARGET_X =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Y =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TARGET_Z =
            SynchedEntityData.defineId(
                    KissBombEntity.class, EntityDataSerializers.FLOAT);

    public KissBombEntity(
            EntityType<? extends KissBombEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(TARGET_UUID, Optional.empty());
        builder.define(TARGET_BLOCK, BlockPos.ZERO);
        builder.define(BLOCK_TARGET, false);
        builder.define(TARGET_X, 0.0F);
        builder.define(TARGET_Y, 0.0F);
        builder.define(TARGET_Z, 0.0F);
    }

    public boolean launch(
            ServerPlayer owner,
            StandEntity stand,
            KissTokenService.Token token) {
        entityData.set(OWNER_UUID, Optional.of(owner.getUUID()), true);
        entityData.set(STAND_UUID, Optional.of(stand.getUUID()), true);
        Vec3 targetPosition;
        if (token.isEntity()) {
            Entity target = owner.serverLevel().getEntity(token.entityUuid());
            if (target == null || !target.isAlive()) {
                return false;
            }
            entityData.set(TARGET_UUID, Optional.of(token.entityUuid()), true);
            entityData.set(BLOCK_TARGET, false, true);
            targetPosition = target.position();
        } else {
            if (token.blockPos() == null
                    || owner.level().getBlockState(token.blockPos()).isAir()) {
                return false;
            }
            entityData.set(TARGET_BLOCK, token.blockPos(), true);
            entityData.set(BLOCK_TARGET, true, true);
            targetPosition = Vec3.atCenterOf(token.blockPos())
                    .add(Vec3.atLowerCornerOf(token.face().getNormal()).scale(0.6D));
        }
        setTarget(targetPosition);
        setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.35D)));
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (!(level() instanceof ServerLevel level)) {
            move(MoverType.SELF, getDeltaMovement());
            return;
        }
        if (!KissService.validController(level, getOwnerUuid(), getStandUuid())) {
            discard();
            return;
        }
        if (tickCount > LIFETIME_TICKS) {
            explode(level);
            return;
        }
        refreshMovingTarget(level);
        Vec3 delta = targetPosition().subtract(position());
        // A clamped move can leave the distance a few ULPs above the boundary.
        double arrival = ARRIVAL_DISTANCE + 1.0E-6D;
        if (delta.lengthSqr() <= arrival * arrival) {
            explode(level);
            return;
        }
        double distance = delta.length();
        double step = Math.min(SPEED, distance - ARRIVAL_DISTANCE);
        setDeltaMovement(delta.scale(step / distance));
        hasImpulse = true;
        move(MoverType.SELF, getDeltaMovement());
    }

    private void refreshMovingTarget(ServerLevel level) {
        UUID targetUuid = getTargetUuid();
        Entity target = targetUuid != null ? level.getEntity(targetUuid) : null;
        if (target != null && target.isAlive()) {
            setTarget(target.position().add(0.0D, target.getBbHeight() * 0.7D, 0.0D));
        }
    }

    private void explode(ServerLevel level) {
        UUID ownerUuid = getOwnerUuid();
        ServerPlayer owner = ownerUuid != null
                ? level.getServer().getPlayerList().getPlayer(ownerUuid)
                : null;
        AABB damageArea = getBoundingBox().inflate(DAMAGE_RADIUS);
        for (LivingEntity living : level.getEntitiesOfClass(
                LivingEntity.class,
                damageArea,
                target -> target.isAlive()
                        && target != owner
                        && !(target instanceof StandEntity))) {
            living.hurt(level.damageSources().magic(), DAMAGE);
        }
        KissService.removeMatchingStickers(
                level,
                ownerUuid,
                getTargetUuid(),
                isBlockTarget() ? getTargetBlock() : null);
        level.explode(this, getX(), getY(), getZ(),
                1.0F, Level.ExplosionInteraction.NONE);
        discard();
    }

    private void setTarget(Vec3 target) {
        entityData.set(TARGET_X, (float) target.x, true);
        entityData.set(TARGET_Y, (float) target.y, true);
        entityData.set(TARGET_Z, (float) target.z, true);
    }

    public Vec3 targetPosition() {
        return new Vec3(
                entityData.get(TARGET_X),
                entityData.get(TARGET_Y),
                entityData.get(TARGET_Z));
    }

    public UUID getOwnerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public UUID getTargetUuid() {
        return entityData.get(TARGET_UUID).orElse(null);
    }

    public boolean isBlockTarget() {
        return entityData.get(BLOCK_TARGET);
    }

    public BlockPos getTargetBlock() {
        return entityData.get(TARGET_BLOCK);
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID).filter(ownerUuid::equals).isPresent();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isInvulnerable() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER_UUID).ifPresent(uuid -> tag.putUUID("Owner", uuid));
        entityData.get(STAND_UUID).ifPresent(uuid -> tag.putUUID("Stand", uuid));
        entityData.get(TARGET_UUID).ifPresent(uuid -> tag.putUUID("Target", uuid));
        tag.putLong("TargetBlock", getTargetBlock().asLong());
        tag.putBoolean("BlockTarget", isBlockTarget());
        Vec3 target = targetPosition();
        tag.putDouble("TargetX", target.x);
        tag.putDouble("TargetY", target.y);
        tag.putDouble("TargetZ", target.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER_UUID,
                tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(STAND_UUID,
                tag.hasUUID("Stand") ? Optional.of(tag.getUUID("Stand")) : Optional.empty());
        entityData.set(TARGET_UUID,
                tag.hasUUID("Target") ? Optional.of(tag.getUUID("Target")) : Optional.empty());
        entityData.set(TARGET_BLOCK, BlockPos.of(tag.getLong("TargetBlock")));
        entityData.set(BLOCK_TARGET, tag.getBoolean("BlockTarget"));
        setTarget(new Vec3(
                tag.getDouble("TargetX"),
                tag.getDouble("TargetY"),
                tag.getDouble("TargetZ")));
    }
}

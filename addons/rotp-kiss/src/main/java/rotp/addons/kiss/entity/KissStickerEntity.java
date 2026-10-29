package rotp.addons.kiss.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.kiss.service.KissService;
import rotp.addons.kiss.service.KissTokenService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class KissStickerEntity extends Entity {
    public enum Mode {
        FLYING,
        LIVING,
        BLOCK
    }

    public static final int FLYING_LIFETIME_TICKS = 100;
    public static final double FLIGHT_SPEED = 1.8D;
    public static final double MAX_FLIGHT_DISTANCE = 20.0D;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> STAND_UUID =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<BlockPos> BLOCK_POS =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FACE =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> MODE =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> CREATED_AT =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> RESERVED =
            SynchedEntityData.defineId(
                    KissStickerEntity.class, EntityDataSerializers.BOOLEAN);
    private Vec3 launchOrigin;
    private boolean tokenGranted;

    public KissStickerEntity(
            EntityType<? extends KissStickerEntity> type, Level level) {
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
        builder.define(BLOCK_POS, BlockPos.ZERO);
        builder.define(FACE, Direction.UP.get3DDataValue());
        builder.define(MODE, (byte) Mode.FLYING.ordinal());
        builder.define(CREATED_AT, 0L);
        builder.define(RESERVED, false);
    }

    public void launch(ServerPlayer owner, StandEntity stand) {
        bindOwner(owner, stand);
        setMode(Mode.FLYING);
        setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.35D)));
        launchOrigin = position();
        setDeltaMovement(owner.getLookAngle().normalize().scale(FLIGHT_SPEED));
        hasImpulse = true;
    }

    public void attachLiving(
            ServerPlayer owner,
            StandEntity stand,
            LivingEntity target,
            Vec3 hitLocation) {
        bindOwner(owner, stand);
        attachLiving(target, hitLocation);
    }

    public void attachBlock(
            ServerPlayer owner,
            StandEntity stand,
            BlockPos position,
            Direction face) {
        bindOwner(owner, stand);
        attachBlock(position, face);
    }

    private void bindOwner(ServerPlayer owner, StandEntity stand) {
        entityData.set(OWNER_UUID, Optional.of(owner.getUUID()), true);
        entityData.set(STAND_UUID, Optional.of(stand.getUUID()), true);
        entityData.set(CREATED_AT, owner.level().getGameTime(), true);
    }

    private void attachLiving(LivingEntity target, Vec3 hitLocation) {
        setMode(Mode.LIVING);
        entityData.set(TARGET_UUID, Optional.of(target.getUUID()), true);
        Vec3 position = hitLocation != null
                ? hitLocation
                : target.position().add(0.0D, target.getBbHeight() * 0.7D, 0.0D);
        setPos(position);
        setDeltaMovement(Vec3.ZERO);
        grantEntityToken(target);
    }

    private void attachBlock(BlockPos position, Direction face) {
        setMode(Mode.BLOCK);
        entityData.set(BLOCK_POS, position.immutable(), true);
        entityData.set(FACE, face.get3DDataValue(), true);
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()).scale(0.505D);
        setPos(Vec3.atCenterOf(position).add(normal));
        setDeltaMovement(Vec3.ZERO);
        grantBlockToken(position, face);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        noPhysics = true;
        if (!(level() instanceof ServerLevel level)) {
            if (mode() == Mode.FLYING) {
                setPos(position().add(getDeltaMovement()));
            }
            return;
        }
        if (!KissService.validController(level, getOwnerUuid(), getStandUuid())) {
            discard();
            return;
        }
        switch (mode()) {
            case FLYING -> tickFlying();
            case LIVING -> tickLiving(level);
            case BLOCK -> tickBlock();
        }
    }

    private void tickFlying() {
        if (tickCount > FLYING_LIFETIME_TICKS) {
            discard();
            return;
        }
        if (launchOrigin != null
                && position().distanceToSqr(launchOrigin)
                        > MAX_FLIGHT_DISTANCE * MAX_FLIGHT_DISTANCE) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(
                this, this::canHitEntity);
        if (hit instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living) {
            attachLiving(living, entityHit.getLocation());
            return;
        }
        if (hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK) {
            attachBlock(blockHit.getBlockPos(), blockHit.getDirection());
            return;
        }
        setPos(position().add(getDeltaMovement()));
    }

    private boolean canHitEntity(Entity entity) {
        return entity instanceof LivingEntity
                && entity.isAlive()
                && !(entity instanceof StandEntity)
                && !entity.getUUID().equals(getOwnerUuid());
    }

    private void tickLiving(ServerLevel level) {
        Entity target = attachedEntity();
        if (target == null) {
            // The UUID token can pull the target back after it becomes available again.
            discard();
            return;
        }
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            discard();
            return;
        }
        setPos(living.position().add(0.0D, living.getBbHeight() * 0.7D, 0.0D));
    }

    private void tickBlock() {
        BlockPos position = getBlockPos();
        if (level().getBlockState(position).isAir()) {
            discard();
            return;
        }
        Direction face = getFace();
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal()).scale(0.505D);
        setPos(Vec3.atCenterOf(position).add(normal));
    }

    public Mode mode() {
        int ordinal = entityData.get(MODE);
        return ordinal >= 0 && ordinal < Mode.values().length
                ? Mode.values()[ordinal]
                : Mode.FLYING;
    }

    private void setMode(Mode mode) {
        entityData.set(MODE, (byte) mode.ordinal(), true);
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

    public BlockPos getBlockPos() {
        return entityData.get(BLOCK_POS);
    }

    public Direction getFace() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    public long createdAt() {
        return entityData.get(CREATED_AT);
    }

    public boolean isReserved() {
        return entityData.get(RESERVED);
    }

    public boolean reserve() {
        if (isReserved() || mode() == Mode.FLYING) {
            return false;
        }
        entityData.set(RESERVED, true, true);
        return true;
    }

    private void grantEntityToken(LivingEntity target) {
        ServerPlayer owner = ownerPlayer();
        if (!tokenGranted && owner != null) {
            KissTokenService.grantEntityToken(owner, target);
            tokenGranted = true;
        }
    }

    private void grantBlockToken(BlockPos position, Direction face) {
        ServerPlayer owner = ownerPlayer();
        if (!tokenGranted && owner != null) {
            KissTokenService.grantBlockToken(owner, position, face);
            tokenGranted = true;
        }
    }

    private ServerPlayer ownerPlayer() {
        UUID ownerUuid = getOwnerUuid();
        return ownerUuid != null && level().getServer() != null
                ? level().getServer().getPlayerList().getPlayer(ownerUuid)
                : null;
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID).filter(ownerUuid::equals).isPresent();
    }

    public Entity attachedEntity() {
        UUID targetUuid = getTargetUuid();
        if (targetUuid == null) {
            return null;
        }
        if (level() instanceof ServerLevel serverLevel) {
            return serverLevel.getEntity(targetUuid);
        }
        return level().getEntities(
                        this,
                        getBoundingBox().inflate(96.0D),
                        entity -> targetUuid.equals(entity.getUUID()))
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Vec3 targetPosition() {
        Entity target = attachedEntity();
        return target != null
                ? target.position().add(0.0D, target.getBbHeight() * 0.7D, 0.0D)
                : position();
    }

    public boolean matchesAttachment(UUID targetUuid, BlockPos blockPos) {
        return mode() == Mode.LIVING
                && targetUuid != null
                && targetUuid.equals(getTargetUuid())
                || mode() == Mode.BLOCK
                        && blockPos != null
                        && blockPos.equals(getBlockPos());
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
        tag.putLong("BlockPos", getBlockPos().asLong());
        tag.putInt("Face", entityData.get(FACE));
        tag.putByte("Mode", entityData.get(MODE));
        tag.putLong("CreatedAt", createdAt());
        tag.putBoolean("Reserved", isReserved());
        tag.putBoolean("TokenGranted", tokenGranted);
        if (launchOrigin != null) {
            tag.putDouble("LaunchX", launchOrigin.x);
            tag.putDouble("LaunchY", launchOrigin.y);
            tag.putDouble("LaunchZ", launchOrigin.z);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(OWNER_UUID,
                tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        entityData.set(STAND_UUID,
                tag.hasUUID("Stand") ? Optional.of(tag.getUUID("Stand")) : Optional.empty());
        entityData.set(TARGET_UUID,
                tag.hasUUID("Target") ? Optional.of(tag.getUUID("Target")) : Optional.empty());
        entityData.set(BLOCK_POS, BlockPos.of(tag.getLong("BlockPos")));
        entityData.set(FACE, tag.getInt("Face"));
        entityData.set(MODE, tag.getByte("Mode"));
        entityData.set(CREATED_AT, tag.getLong("CreatedAt"));
        entityData.set(RESERVED, tag.getBoolean("Reserved"));
        tokenGranted = tag.getBoolean("TokenGranted");
        if (tag.contains("LaunchX")) {
            launchOrigin = new Vec3(
                    tag.getDouble("LaunchX"),
                    tag.getDouble("LaunchY"),
                    tag.getDouble("LaunchZ"));
        }
    }
}

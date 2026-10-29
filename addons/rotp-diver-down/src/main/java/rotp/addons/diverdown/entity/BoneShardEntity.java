package rotp.addons.diverdown.entity;

import java.util.Optional;
import java.util.UUID;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.BlockHitResult;

public final class BoneShardEntity extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 360;
    public static final float BASE_DAMAGE = 5.0F;
    public static final float MAX_HARDNESS = 0.0F;
    public static final double GRAVITY = 0.05D;

    private static final EntityDataAccessor<Optional<UUID>>
            CONTROLLER_UUID = SynchedEntityData.defineId(
                    BoneShardEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            STAND_UUID = SynchedEntityData.defineId(
                    BoneShardEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> EXPIRES_AT =
            SynchedEntityData.defineId(
                    BoneShardEntity.class,
                    EntityDataSerializers.LONG);
    private static final String DIMENSION_TAG = "Dimension";
    private String dimension = "";

    public BoneShardEntity(
            EntityType<? extends BoneShardEntity> type,
            Level level) {
        super(type, level);
    }

    public BoneShardEntity(
            LivingEntity origin,
            LivingEntity controller,
            DiverDownStandEntity stand,
            Level level) {
        super(DiverDownAddon.BONE_SHARD_ENTITY.get(), origin, level);
        bind(controller, stand);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CONTROLLER_UUID, Optional.empty());
        builder.define(STAND_UUID, Optional.empty());
        builder.define(EXPIRES_AT, 0L);
    }

    public UUID getControllerUuid() {
        return entityData.get(CONTROLLER_UUID).orElse(null);
    }

    public UUID getStandUuid() {
        return entityData.get(STAND_UUID).orElse(null);
    }

    public void bind(
            LivingEntity controller,
            DiverDownStandEntity stand) {
        if (!(level() instanceof ServerLevel level)
                || !DiverDownAuthority.validController(
                        controller,
                        stand,
                        DiverDownAddon.BONE_SHARD_BOMB_NAME)) {
            discard();
            return;
        }
        entityData.set(
                CONTROLLER_UUID,
                Optional.of(controller.getUUID()),
                true);
        entityData.set(
                STAND_UUID,
                Optional.of(stand.getUUID()),
                true);
        entityData.set(
                EXPIRES_AT,
                level.getGameTime() + LIFESPAN_TICKS,
                true);
        dimension = level.dimension().location().toString();
        DiverDownAuthority.markOwner(controller);
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel level
                && !isAuthorized(level)) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    // 1.16 BoneShardEntity.afterBlockHit :74-91: a block the shard does not break plays its hit sound an octave down; one
    // it breaks slows it by a tenth and it strikes what lies on the rest of its way this tick, before it goes
    @Override
    protected void afterBlockHit(BlockHitResult blockHit, boolean blockDestroyed) {
        if (blockDestroyed) {
            if (!level().isClientSide()) {
                setDeltaMovement(getDeltaMovement().scale(0.9D));
                checkHit();
            }
        } else {
            BlockPos pos = blockHit.getBlockPos();
            SoundType sound = level().getBlockState(pos).getSoundType(level(), pos, this);
            level().playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 1.0F, sound.getPitch() * 0.5F);
        }
    }

    @Override
    protected boolean hasGravity() {
        return true;
    }

    @Override
    protected boolean constVelocity() {
        return false;
    }

    @Override
    protected double getGravityAcceleration() {
        return GRAVITY;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID controller = getControllerUuid();
        UUID stand = getStandUuid();
        if (controller != null) {
            tag.putUUID("Controller", controller);
        }
        if (stand != null) {
            tag.putUUID("Stand", stand);
        }
        tag.putLong("ExpiresAt", entityData.get(EXPIRES_AT));
        tag.putString(DIMENSION_TAG, dimension);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                CONTROLLER_UUID,
                tag.hasUUID("Controller")
                        ? Optional.of(tag.getUUID("Controller"))
                        : Optional.empty());
        entityData.set(
                STAND_UUID,
                tag.hasUUID("Stand")
                        ? Optional.of(tag.getUUID("Stand"))
                        : Optional.empty());
        entityData.set(EXPIRES_AT, tag.getLong("ExpiresAt"));
        dimension = tag.getString(DIMENSION_TAG);
    }

    private boolean isAuthorized(ServerLevel level) {
        UUID controllerUuid = getControllerUuid();
        UUID standUuid = getStandUuid();
        if (controllerUuid == null
                || standUuid == null
                || level.getGameTime()
                        >= entityData.get(EXPIRES_AT)
                || !level.dimension().location().toString().equals(
                        dimension)) {
            return false;
        }
        Entity controllerEntity =
                level.getEntity(controllerUuid);
        Entity standEntity = level.getEntity(standUuid);
        return controllerEntity instanceof LivingEntity controller
                && standEntity
                        instanceof DiverDownStandEntity stand
                && DiverDownAuthority.validController(
                        controller,
                        stand,
                        DiverDownAddon.BONE_SHARD_BOMB_NAME);
    }
}

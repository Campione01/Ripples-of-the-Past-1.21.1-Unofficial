package rotp.addons.badcompany.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public final class BadCompanyMissileEntity
        extends ModdedProjectileEntity
        implements BadCompanyOwnedEntity {
    public static final int LIFETIME_TICKS = 100;
    public static final float BLOCK_EXPLOSION_RADIUS = 1.75F;
    public static final float ENTITY_EXPLOSION_RADIUS = 2.0F;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    BadCompanyMissileEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    BadCompanyMissileEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    @Nullable
    private BadCompanyStandEntity cachedCoordinator;
    @Nullable
    private UUID lifecycleProducerUuid;
    @Nullable
    private LivingEntity cachedLifecycleProducer;
    private boolean retainedExplosiveMode;

    public BadCompanyMissileEntity(
            EntityType<? extends BadCompanyMissileEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public BadCompanyMissileEntity(
            LivingEntity shooter, Level level) {
        super(BadCompanyAddon.BAD_MISSILE.get(), shooter, level);
        setNoGravity(true);
    }

    public void bindClosure(
            UUID ownerUuid, UUID coordinatorUuid) {
        if (!level().isClientSide()) {
            entityData.set(
                    OWNER_UUID, Optional.ofNullable(ownerUuid));
            entityData.set(
                    COORDINATOR_UUID,
                    Optional.ofNullable(coordinatorUuid));
            cachedCoordinator = null;
        }
    }

    public void bindLifecycleProducer(LivingEntity producer) {
        if (!level().isClientSide()) {
            lifecycleProducerUuid = producer.getUUID();
            cachedLifecycleProducer = producer;
            captureCoordinatorMode(resolveCoordinator());
        }
    }

    public boolean usesOwnerLifetime() {
        return lifecycleProducerUuid != null
                && lifecycleProducerUuid.equals(
                        entityData.get(OWNER_UUID).orElse(null));
    }

    public void captureCoordinatorMode(
            @Nullable BadCompanyStandEntity coordinator) {
        if (!level().isClientSide() && coordinator != null
                && isBoundTo(coordinator.getUUID())) {
            retainedExplosiveMode = coordinator.explosiveMissiles();
        }
    }

    @Override
    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    @Override
    public boolean isBoundTo(UUID coordinatorUuid) {
        return coordinatorUuid != null
                && entityData.get(COORDINATOR_UUID)
                        .filter(coordinatorUuid::equals)
                        .isPresent();
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel
                && lifecycleProducerUuid != null) {
            if (!hasLivingLifecycleProducer(serverLevel)) {
                discard();
                return;
            }
            captureCoordinatorMode(resolveCoordinator());
        }
        super.tick();
        setNoGravity(true);
        if (isRemoved()) {
            return;
        }
        if (!level().isClientSide()
                && lifecycleProducerUuid == null
                && (!(level() instanceof ServerLevel serverLevel)
                || !hasValidClosure(serverLevel))) {
            discard();
        }
    }

    private boolean hasLivingLifecycleProducer(ServerLevel level) {
        if (cachedLifecycleProducer != null) {
            if (!cachedLifecycleProducer.isAlive()) {
                return false;
            }
            if (cachedLifecycleProducer.level() == level) {
                return true;
            }
            cachedLifecycleProducer = null;
        }
        Entity producer = level.getEntity(lifecycleProducerUuid);
        if (producer == null) {
            // Unavailable producers are retried, bounded by the missile's own lifetime.
            return true;
        }
        if (!(producer instanceof LivingEntity living)) {
            return false;
        }
        cachedLifecycleProducer = living;
        return living.isAlive();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        explode(ENTITY_EXPLOSION_RADIUS);
        super.onHitEntity(result);
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode(BLOCK_EXPLOSION_RADIUS);
        super.onHitBlock(result);
    }

    private void explode(float radius) {
        if (!(level() instanceof ServerLevel serverLevel)
                || isRemoved()) {
            return;
        }
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        captureCoordinatorMode(coordinator);
        Entity owner = getOwner();
        boolean explosiveMode = lifecycleProducerUuid != null
                ? retainedExplosiveMode
                : coordinator != null && coordinator.explosiveMissiles();
        boolean breaksBlocks = explosiveMode
                && serverLevel.getGameRules().getBoolean(
                        GameRules.RULE_MOBGRIEFING);
        serverLevel.explode(
                owner,
                getX(),
                getY(),
                getZ(),
                radius,
                breaksBlocks
                        ? Level.ExplosionInteraction.MOB
                        : Level.ExplosionInteraction.NONE);
    }

    private boolean hasValidClosure(ServerLevel level) {
        UUID ownerUuid = entityData.get(OWNER_UUID)
                .orElse(null);
        UUID coordinatorUuid = entityData.get(COORDINATOR_UUID)
                .orElse(null);
        Entity owner = ownerUuid != null
                ? level.getEntity(ownerUuid)
                : null;
        Entity coordinator = coordinatorUuid != null
                ? level.getEntity(coordinatorUuid)
                : null;
        return owner instanceof LivingEntity livingOwner
                && coordinator
                        instanceof BadCompanyStandEntity stand
                && stand.isActiveFor(livingOwner)
                && stand.isOwnedBy(ownerUuid);
    }

    @Nullable
    @Override
    public BadCompanyStandEntity resolveCoordinator() {
        UUID uuid = entityData.get(COORDINATOR_UUID)
                .orElse(null);
        if (uuid == null) {
            return null;
        }
        if (cachedCoordinator != null
                && !cachedCoordinator.isRemoved()
                && uuid.equals(cachedCoordinator.getUUID())) {
            return cachedCoordinator;
        }
        Entity entity = level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(uuid)
                : level().getEntitiesOfClass(
                        BadCompanyStandEntity.class,
                        getBoundingBox().inflate(
                                BadCompanyStandEntity
                                        .LEGACY_MAX_RANGE + 8.0D),
                        stand -> uuid.equals(stand.getUUID()))
                        .stream()
                        .findFirst()
                        .orElse(null);
        cachedCoordinator = entity
                instanceof BadCompanyStandEntity stand
                        ? stand
                        : null;
        return cachedCoordinator;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) {
            return false;
        }
        UUID ownerUuid = entityData.get(OWNER_UUID)
                .orElse(null);
        return !(entity instanceof BadCompanyOwnedEntity owned)
                || !owned.isOwnedBy(ownerUuid);
    }

    @Override
    protected float getBaseDamage() {
        return 0.0F;
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
    public int ticksLifespan() {
        return LIFETIME_TICKS;
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
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(COORDINATOR_UUID, Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lifecycleProducerUuid != null) {
            captureCoordinatorMode(resolveCoordinator());
            tag.putUUID("BadCompanyLifecycleProducer", lifecycleProducerUuid);
            tag.putBoolean("BadCompanyExplosiveMode", retainedExplosiveMode);
        }
        entityData.get(OWNER_UUID)
                .ifPresent(uuid ->
                        tag.putUUID("BadCompanyOwner", uuid));
        entityData.get(COORDINATOR_UUID)
                .ifPresent(uuid ->
                        tag.putUUID(
                                "BadCompanyCoordinator", uuid));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lifecycleProducerUuid = tag.hasUUID("BadCompanyLifecycleProducer")
                ? tag.getUUID("BadCompanyLifecycleProducer") : null;
        cachedLifecycleProducer = null;
        retainedExplosiveMode = tag.getBoolean("BadCompanyExplosiveMode");
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("BadCompanyOwner")
                        ? Optional.of(
                                tag.getUUID("BadCompanyOwner"))
                        : Optional.empty());
        entityData.set(
                COORDINATOR_UUID,
                tag.hasUUID("BadCompanyCoordinator")
                        ? Optional.of(tag.getUUID(
                                "BadCompanyCoordinator"))
                        : Optional.empty());
        cachedCoordinator = null;
    }

    @Override
    public void remove(RemovalReason reason) {
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (!level().isClientSide() && coordinator != null) {
            coordinator.untrackTransient(getUUID());
        }
        cachedCoordinator = null;
        cachedLifecycleProducer = null;
        super.remove(reason);
    }
}

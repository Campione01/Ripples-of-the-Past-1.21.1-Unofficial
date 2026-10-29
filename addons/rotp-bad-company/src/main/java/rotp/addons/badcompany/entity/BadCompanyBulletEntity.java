package rotp.addons.badcompany.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BadCompanyBulletEntity
        extends ModdedProjectileEntity
        implements BadCompanyOwnedEntity {
    public static final float BASE_DAMAGE = 5.0F;
    public static final float MAX_BREAKABLE_HARDNESS = 0.3F;
    public static final int LIFETIME_TICKS = 40;
    public static final int MAX_TRACE_POINTS = 64;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    BadCompanyBulletEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    BadCompanyBulletEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public final List<Vec3> tracePos = new LinkedList<>();
    public Vec3 initialPos;
    @Nullable
    private BadCompanyStandEntity cachedCoordinator;
    @Nullable
    private UUID lifecycleProducerUuid;
    @Nullable
    private LivingEntity cachedLifecycleProducer;

    public BadCompanyBulletEntity(
            EntityType<? extends BadCompanyBulletEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public BadCompanyBulletEntity(
            LivingEntity shooter, Level level) {
        super(BadCompanyAddon.BAD_BULLET.get(), shooter, level);
        setNoGravity(true);
    }

    public void bindClosure(
            UUID ownerUuid, UUID coordinatorUuid) {
        if (level().isClientSide()) {
            return;
        }
        entityData.set(
                OWNER_UUID, Optional.ofNullable(ownerUuid));
        entityData.set(
                COORDINATOR_UUID,
                Optional.ofNullable(coordinatorUuid));
        cachedCoordinator = null;
    }

    public void bindLifecycleProducer(LivingEntity producer) {
        if (!level().isClientSide()) {
            lifecycleProducerUuid = producer.getUUID();
            cachedLifecycleProducer = producer;
        }
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null
                && entityData.get(OWNER_UUID)
                        .filter(ownerUuid::equals)
                        .isPresent();
    }

    public boolean isBoundTo(UUID coordinatorUuid) {
        return coordinatorUuid != null
                && entityData.get(COORDINATOR_UUID)
                        .filter(coordinatorUuid::equals)
                        .isPresent();
    }

    @Override
    public void tick() {
        if (level() instanceof ServerLevel serverLevel
                && lifecycleProducerUuid != null
                && !hasLivingLifecycleProducer(serverLevel)) {
            discard();
            return;
        }
        super.tick();
        if (isRemoved()) {
            return;
        }
        setNoGravity(true);

        if (level().isClientSide()) {
            appendTrace(position());
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)
                || !hasValidClosure(serverLevel)) {
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
            // Retry unavailable producers within the bullet's unchanged lifetime.
            return true;
        }
        if (!(producer instanceof LivingEntity living)) {
            return false;
        }
        cachedLifecycleProducer = living;
        return living.isAlive();
    }

    private boolean hasValidClosure(ServerLevel level) {
        UUID ownerUuid = entityData.get(OWNER_UUID)
                .orElse(null);
        UUID coordinatorUuid =
                entityData.get(COORDINATOR_UUID)
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
    public BadCompanyStandEntity resolveCoordinator() {
        UUID coordinatorUuid = entityData.get(COORDINATOR_UUID)
                .orElse(null);
        if (coordinatorUuid == null) {
            return null;
        }
        if (cachedCoordinator != null
                && !cachedCoordinator.isRemoved()
                && cachedCoordinator.level() == level()
                && coordinatorUuid.equals(
                        cachedCoordinator.getUUID())) {
            return cachedCoordinator;
        }
        Entity resolved = level() instanceof ServerLevel serverLevel
                ? serverLevel.getEntity(coordinatorUuid)
                : level().getEntitiesOfClass(
                        BadCompanyStandEntity.class,
                        getBoundingBox().inflate(
                                BadCompanyStandEntity
                                        .LEGACY_MAX_RANGE + 8.0D),
                        stand -> coordinatorUuid.equals(
                                stand.getUUID()))
                        .stream()
                        .findFirst()
                        .orElse(null);
        cachedCoordinator = resolved
                instanceof BadCompanyStandEntity stand
                        ? stand
                        : null;
        return cachedCoordinator;
    }

    private void appendTrace(Vec3 position) {
        boolean add = tracePos.isEmpty()
                || position.distanceToSqr(
                        tracePos.get(tracePos.size() - 1))
                        >= 0.05D;
        if (add) {
            tracePos.add(position);
            while (tracePos.size() > MAX_TRACE_POINTS) {
                tracePos.remove(0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) {
            return false;
        }
        UUID ownerUuid = entityData.get(OWNER_UUID)
                .orElse(null);
        UUID coordinatorUuid =
                entityData.get(COORDINATOR_UUID)
                        .orElse(null);
        if (ownerUuid != null
                && ownerUuid.equals(entity.getUUID())
                || coordinatorUuid != null
                && coordinatorUuid.equals(entity.getUUID())) {
            return false;
        }
        return !(entity instanceof BadCompanyOwnedEntity owned)
                || !owned.isOwnedBy(ownerUuid);
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_BREAKABLE_HARDNESS;
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
    public void readSpawnData(
            RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        initialPos = position();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (lifecycleProducerUuid != null) {
            tag.putUUID("BadCompanyLifecycleProducer", lifecycleProducerUuid);
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
        tracePos.clear();
        initialPos = null;
        cachedCoordinator = null;
        cachedLifecycleProducer = null;
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}

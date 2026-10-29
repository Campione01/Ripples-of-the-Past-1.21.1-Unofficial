package rotp.addons.badcompany.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.badcompany.BadCompanyAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class BadCompanyMineEntity
        extends Entity
        implements BadCompanyOwnedEntity {
    public static final int ARMING_TICKS = 20;
    public static final float CONTACT_EXPLOSION_RADIUS = 1.7F;

    private static final EntityDataAccessor<Optional<UUID>>
            OWNER_UUID = SynchedEntityData.defineId(
                    BadCompanyMineEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Optional<UUID>>
            COORDINATOR_UUID = SynchedEntityData.defineId(
                    BadCompanyMineEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> ARMED =
            SynchedEntityData.defineId(
                    BadCompanyMineEntity.class,
                    EntityDataSerializers.BOOLEAN);

    @Nullable
    private BadCompanyStandEntity cachedCoordinator;

    public BadCompanyMineEntity(
            EntityType<? extends BadCompanyMineEntity> type,
            Level level) {
        super(type, level);
        noPhysics = false;
    }

    public void bindClosure(
            UUID ownerUuid, UUID coordinatorUuid) {
        if (!level().isClientSide()) {
            entityData.set(
                    OWNER_UUID, Optional.ofNullable(ownerUuid));
            entityData.set(
                    COORDINATOR_UUID,
                    Optional.ofNullable(coordinatorUuid));
            entityData.set(ARMED, false);
        }
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(COORDINATOR_UUID, Optional.empty());
        builder.define(ARMED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity owner = resolveOwner(serverLevel);
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (owner == null
                || coordinator == null
                || !coordinator.isActiveFor(owner)
                || owner.distanceToSqr(this) > 900.0D) {
            discard();
            return;
        }
        if (!entityData.get(ARMED)
                && tickCount >= ARMING_TICKS) {
            entityData.set(ARMED, true);
        }
        if (!entityData.get(ARMED)) {
            return;
        }
        boolean hostilePresent = serverLevel
                .getEntitiesOfClass(
                        LivingEntity.class,
                        getBoundingBox().inflate(0.6D),
                        entity -> entity.isAlive()
                                && entity != owner
                                && !entity.isAlliedTo(owner)
                                && !(entity
                                        instanceof
                                                BadCompanyOwnedEntity))
                .stream()
                .findAny()
                .isPresent();
        if (hostilePresent) {
            detonate(owner, CONTACT_EXPLOSION_RADIUS);
        }
    }

    public void detonate(
            LivingEntity owner, float radius) {
        if (!(level() instanceof ServerLevel serverLevel)
                || isRemoved()) {
            return;
        }
        serverLevel.explode(
                owner,
                getX(),
                getY(),
                getZ(),
                radius,
                Level.ExplosionInteraction.NONE);
        discard();
    }

    @Nullable
    private LivingEntity resolveOwner(ServerLevel level) {
        return entityData.get(OWNER_UUID)
                .map(level::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .orElse(null);
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
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER_UUID)
                .ifPresent(uuid ->
                        tag.putUUID("BadCompanyOwner", uuid));
        entityData.get(COORDINATOR_UUID)
                .ifPresent(uuid ->
                        tag.putUUID(
                                "BadCompanyCoordinator", uuid));
        tag.putBoolean("BadCompanyArmed", entityData.get(ARMED));
        tag.putInt("Age", Math.max(0, tickCount));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
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
        entityData.set(
                ARMED, tag.getBoolean("BadCompanyArmed"));
        tickCount = Math.max(0, tag.getInt("Age"));
        cachedCoordinator = null;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        BadCompanyStandEntity coordinator =
                resolveCoordinator();
        if (!level().isClientSide() && coordinator != null) {
            coordinator.untrackMine(getUUID());
        }
        cachedCoordinator = null;
        super.remove(reason);
    }
}

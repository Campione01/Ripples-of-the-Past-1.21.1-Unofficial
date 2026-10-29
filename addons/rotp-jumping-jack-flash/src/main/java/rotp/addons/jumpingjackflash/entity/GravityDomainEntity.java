package rotp.addons.jumpingjackflash.entity;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.state.GravityLeaseService;
import rotp.addons.jumpingjackflash.state.GravityLeaseService.Source;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class GravityDomainEntity extends Entity {
    public static final float MAX_RADIUS = 20.0F;
    public static final int EXPAND_TICKS = 10;
    public static final int CLOSE_TICKS = 10;
    public static final float DAMAGE_PER_SECOND = 1.0F;

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(
                    GravityDomainEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Long> START_TICK =
            SynchedEntityData.defineId(
                    GravityDomainEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> CLOSING =
            SynchedEntityData.defineId(
                    GravityDomainEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Long> CLOSE_START_TICK =
            SynchedEntityData.defineId(
                    GravityDomainEntity.class,
                    EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> CLOSE_START_RADIUS =
            SynchedEntityData.defineId(
                    GravityDomainEntity.class,
                    EntityDataSerializers.FLOAT);

    private final Set<UUID> affected = new HashSet<>();
    private boolean released;

    public GravityDomainEntity(
            EntityType<? extends GravityDomainEntity> type,
            Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void initialize(LivingEntity owner) {
        entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        entityData.set(START_TICK, level().getGameTime());
        setPos(owner.position());
        GravityLeaseService.markOwner(owner.getUUID());
    }

    public UUID ownerUuid() {
        return entityData.get(OWNER_UUID).orElse(null);
    }

    public boolean isClosing() {
        return entityData.get(CLOSING);
    }

    public float currentRadius(float partialTick) {
        float now = level().getGameTime() + partialTick;
        if (isClosing()) {
            float elapsed = now - entityData.get(CLOSE_START_TICK);
            float progress = Mth.clamp(
                    elapsed / CLOSE_TICKS, 0.0F, 1.0F);
            float smooth = progress * progress * progress
                    * (progress * (progress * 6.0F - 15.0F)
                            + 10.0F);
            return entityData.get(CLOSE_START_RADIUS)
                    * (1.0F - smooth);
        }
        float elapsed = Math.max(
                0.0F, now - entityData.get(START_TICK));
        if (elapsed >= EXPAND_TICKS) {
            return MAX_RADIUS;
        }
        float progress = elapsed / EXPAND_TICKS;
        float eased = 1.0F
                - (float) Math.pow(1.0F - progress, 3.0D);
        return MAX_RADIUS * eased;
    }

    public void pullAndClose() {
        if (level().isClientSide() || isClosing()) {
            return;
        }
        pullEntities();
        entityData.set(
                CLOSE_START_RADIUS, currentRadius(0.0F));
        entityData.set(CLOSE_START_TICK, level().getGameTime());
        entityData.set(CLOSING, true);
        releaseDomainGravity();
    }

    public void terminate() {
        if (!level().isClientSide()) {
            releaseDomainGravity();
            discard();
        }
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide()) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            terminate();
            return;
        }
        UUID ownerId = ownerUuid();
        GravityLeaseService.markOwner(ownerId);
        LivingEntity owner = owner(serverLevel);
        if (owner == null
                || !owner.isAlive()
                || owner.level() != level()
                || !JumpingJackFlashAddon.isUser(owner)) {
            terminate();
            return;
        }
        if (isClosing()) {
            if (level().getGameTime()
                    >= entityData.get(CLOSE_START_TICK)
                            + CLOSE_TICKS) {
                terminate();
            }
            return;
        }
        if (level().getGameTime()
                < entityData.get(START_TICK) + EXPAND_TICKS) {
            return;
        }
        applyDomain(serverLevel, owner);
    }

    private void applyDomain(
            ServerLevel level, LivingEntity owner) {
        AABB area = getBoundingBox().inflate(MAX_RADIUS + 1.0D);
        Set<UUID> current = new HashSet<>();
        long elapsed = level.getGameTime()
                - entityData.get(START_TICK);
        boolean damageTick = elapsed % 20L == 0L;

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class,
                area,
                LivingEntity::isAlive)) {
            if (target.position().distanceToSqr(position())
                    > MAX_RADIUS * MAX_RADIUS) {
                continue;
            }
            current.add(target.getUUID());
            GravityLeaseService.applyDomain(
                    target, owner.getUUID());
            if (damageTick && target != owner) {
                target.hurt(
                        level.damageSources().indirectMagic(this, owner),
                        DAMAGE_PER_SECOND);
            }
        }
        for (ItemEntity item : level.getEntitiesOfClass(
                ItemEntity.class,
                area,
                ItemEntity::isAlive)) {
            if (item.position().distanceToSqr(position())
                    > MAX_RADIUS * MAX_RADIUS) {
                continue;
            }
            current.add(item.getUUID());
            GravityLeaseService.applyDomain(
                    item, owner.getUUID());
        }

        for (UUID previous : Set.copyOf(affected)) {
            if (current.contains(previous)) {
                continue;
            }
            Entity entity = level.getEntity(previous);
            if (entity != null) {
                GravityLeaseService.releaseEntitySource(
                        entity, owner.getUUID(), Source.DOMAIN);
                if (entity == owner && !owner.onGround()) {
                    Vec3 movement = owner.getDeltaMovement();
                    owner.setDeltaMovement(
                            movement.x, -0.5D, movement.z);
                }
            }
        }
        affected.clear();
        affected.addAll(current);
    }

    private void pullEntities() {
        UUID ownerId = ownerUuid();
        if (ownerId == null
                || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        AABB area = getBoundingBox().inflate(MAX_RADIUS);
        for (LivingEntity target : serverLevel.getEntitiesOfClass(
                LivingEntity.class,
                area,
                LivingEntity::isAlive)) {
            pull(target, 0.5D, 0.05D);
        }
        for (ItemEntity item : serverLevel.getEntitiesOfClass(
                ItemEntity.class,
                area,
                ItemEntity::isAlive)) {
            pull(item, 0.75D, 0.08D);
        }
        LivingEntity owner = owner(serverLevel);
        if (owner != null) {
            owner.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void pull(Entity entity, double maximum, double scale) {
        Vec3 toCenter = position().subtract(entity.position());
        double distance = toCenter.length();
        if (distance > 0.5D) {
            entity.setDeltaMovement(
                    entity.getDeltaMovement().add(
                            toCenter.normalize().scale(
                                    Math.min(maximum,
                                            distance * scale))));
        }
    }

    private LivingEntity owner(ServerLevel level) {
        UUID ownerId = ownerUuid();
        Entity owner = ownerId != null
                ? level.getEntity(ownerId)
                : null;
        return owner instanceof LivingEntity living ? living : null;
    }

    private void releaseDomainGravity() {
        if (released) {
            return;
        }
        released = true;
        UUID owner = ownerUuid();
        if (owner != null
                && level() instanceof ServerLevel serverLevel) {
            GravityLeaseService.releaseOwnerSource(
                    serverLevel.getServer(), owner, Source.DOMAIN);
        }
        affected.clear();
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(START_TICK, 0L);
        builder.define(CLOSING, false);
        builder.define(CLOSE_START_TICK, 0L);
        builder.define(CLOSE_START_RADIUS, 0.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(
                OWNER_UUID,
                tag.hasUUID("Owner")
                        ? Optional.of(tag.getUUID("Owner"))
                        : Optional.empty());
        entityData.set(START_TICK, tag.getLong("StartTick"));
        entityData.set(CLOSING, tag.getBoolean("Closing"));
        entityData.set(
                CLOSE_START_TICK, tag.getLong("CloseStartTick"));
        entityData.set(
                CLOSE_START_RADIUS, tag.getFloat("CloseStartRadius"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        entityData.get(OWNER_UUID)
                .ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putLong("StartTick", entityData.get(START_TICK));
        tag.putBoolean("Closing", entityData.get(CLOSING));
        tag.putLong(
                "CloseStartTick", entityData.get(CLOSE_START_TICK));
        tag.putFloat(
                "CloseStartRadius",
                entityData.get(CLOSE_START_RADIUS));
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide()) {
            releaseDomainGravity();
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(MAX_RADIUS);
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}

package rotp.addons.jumpingjackflash.entity;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TrackingBulletEntity
        extends ModdedProjectileEntity implements ItemSupplier {
    public static final int VOLLEY_COUNT = 16;
    public static final float BASE_DAMAGE = 8.0F;
    public static final float TRACKING_RANGE = 20.0F;
    public static final float TRACKING_BLEND = 0.1F;
    public static final int LIFETIME_TICKS = 100;
    public static final int MAX_TRACE_POINTS = 48;

    private static final EntityDataAccessor<Optional<UUID>> TARGET_UUID =
            SynchedEntityData.defineId(
                    TrackingBulletEntity.class,
                    EntityDataSerializers.OPTIONAL_UUID);

    public final List<Vec3> tracePos = new LinkedList<>();
    public Vec3 initialPos;

    public TrackingBulletEntity(
            EntityType<? extends TrackingBulletEntity> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public TrackingBulletEntity(LivingEntity shooter, Level level) {
        super(JumpingJackFlashAddon.TRACKING_BULLET.get(), shooter, level);
        setNoGravity(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        setNoGravity(true);
        if (level().isClientSide()) {
            appendTrace(position());
            return;
        }
        if (!(getOwner() instanceof LivingEntity owner)
                || !owner.isAlive()
                || owner.level() != level()
                || !JumpingJackFlashAddon.isUser(owner)) {
            discard();
            return;
        }
        LivingEntity target = target();
        if (target == null || !validTarget(target)) {
            target = findTarget();
            setTarget(target);
        }
        if (getDeltaMovement().lengthSqr() < 1.0E-4D) {
            discard();
        }
    }

    @Override
    protected void moveProjectile() {
        super.moveProjectile();
        LivingEntity target = target();
        if (target == null || !validTarget(target)) {
            return;
        }
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        Vec3 toTarget = target.getBoundingBox().getCenter()
                .subtract(position());
        if (speed <= 1.0E-4D || toTarget.lengthSqr() <= 0.25D) {
            return;
        }
        Vec3 current = velocity.normalize();
        Vec3 desired = toTarget.normalize();
        Vec3 steered = current.add(
                desired.subtract(current).scale(TRACKING_BLEND));
        if (steered.lengthSqr() > 1.0E-7D) {
            setDeltaMovement(steered.normalize().scale(speed));
        }
    }

    private LivingEntity findTarget() {
        AABB search = getBoundingBox().inflate(TRACKING_RANGE);
        return level().getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        this::validTarget)
                .stream()
                .min(java.util.Comparator.comparingDouble(
                        this::distanceToSqr))
                .orElse(null);
    }

    private boolean validTarget(LivingEntity target) {
        Entity owner = getOwner();
        if (!target.isAlive()
                || target.isRemoved()
                || target == owner
                || target.level() != level()) {
            return false;
        }
        return !(target instanceof StandEntity stand
                && stand.getUser() == owner);
    }

    private void setTarget(LivingEntity target) {
        entityData.set(
                TARGET_UUID,
                target != null
                        ? Optional.of(target.getUUID())
                        : Optional.empty());
    }

    private LivingEntity target() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity target = entityData.get(TARGET_UUID)
                .map(serverLevel::getEntity)
                .orElse(null);
        return target instanceof LivingEntity living ? living : null;
    }

    private void appendTrace(Vec3 pos) {
        if (tracePos.isEmpty()
                || pos.distanceToSqr(
                        tracePos.get(tracePos.size() - 1)) >= 0.05D) {
            tracePos.add(pos);
            while (tracePos.size() > MAX_TRACE_POINTS) {
                tracePos.remove(0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity)
                && (!(entity instanceof LivingEntity living)
                        || validTarget(living));
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE;
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
    public ItemStack getItem() {
        return new ItemStack(Items.IRON_NUGGET);
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
        builder.define(TARGET_UUID, Optional.empty());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        initialPos = position();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(TARGET_UUID)
                .ifPresent(uuid -> tag.putUUID("Target", uuid));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(
                TARGET_UUID,
                tag.hasUUID("Target")
                        ? Optional.of(tag.getUUID("Target"))
                        : Optional.empty());
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

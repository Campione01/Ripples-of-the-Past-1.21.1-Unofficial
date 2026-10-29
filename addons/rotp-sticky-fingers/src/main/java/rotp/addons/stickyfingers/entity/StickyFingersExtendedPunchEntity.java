package rotp.addons.stickyfingers.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.ImmobilizeEffect;
import rotp.core.powersystem.standpower.entity.ManualStandMovementLock.InputDirection;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.util.functions.JojoModUtil;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.StickyFingersPhase1Spec;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class StickyFingersExtendedPunchEntity
        extends OwnerBoundProjectileEntity {
    private static final EntityDataAccessor<Integer> ATTACHED_ENTITY =
            SynchedEntityData.defineId(
                    StickyFingersExtendedPunchEntity.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> PULL_TARGETS =
            SynchedEntityData.defineId(
                    StickyFingersExtendedPunchEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETRACTING =
            SynchedEntityData.defineId(
                    StickyFingersExtendedPunchEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> COLLISION_RESOLVED =
            SynchedEntityData.defineId(
                    StickyFingersExtendedPunchEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private static final UUID MANUAL_MOVEMENT_LOCK =
            UUID.fromString("3f243248-974a-41f6-940e-13554f3dc2fc");
    private static final Vec3 OFFSET =
            new Vec3(-0.35D, -0.2D, 0.1D);
    private static final double PULL_STOP_DISTANCE_SQR = 4.0D;

    private final Set<Entity> draggedEntities = new HashSet<>();
    @Nullable
    private UUID attachedEntityUuid;
    private int lifeSpan =
            StickyFingersPhase1Spec.EXTENDED_PUNCH_PERFORM_TICKS;
    private double distance;
    private boolean movingForward = true;

    public StickyFingersExtendedPunchEntity(
            LivingEntity shooter, Level level) {
        super(
                StickyFingersAddon
                        .STICKY_FINGERS_EXTENDED_PUNCH_ENTITY
                        .get(),
                shooter,
                level);
    }

    public StickyFingersExtendedPunchEntity(
            EntityType<? extends StickyFingersExtendedPunchEntity> type,
            Level level) {
        super(type, level);
    }

    public void setLifeSpan(int lifeSpan) {
        this.lifeSpan = Math.max(1, lifeSpan);
    }

    public void setPullTargets(boolean pullTargets) {
        entityData.set(PULL_TARGETS, pullTargets);
    }

    public boolean pullsTargets() {
        return entityData.get(PULL_TARGETS);
    }

    public boolean isRetracting() {
        return entityData.get(RETRACTING);
    }

    private void setRetracting(boolean retracting) {
        entityData.set(RETRACTING, retracting);
        if (retracting) {
            movingForward = false;
        }
    }

    @Override
    public int ticksLifespan() {
        return lifeSpan;
    }

    @Override
    protected float getBaseDamage() {
        return StickyFingersPhase1Spec.EXTENDED_PUNCH_DAMAGE;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return StickyFingersPhase1Spec
                .EXTENDED_PUNCH_MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    protected boolean shouldFollowOwner() {
        return false;
    }

    @Override
    public boolean isBodyPart() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected Vec3 getOwnerRelativeOffset() {
        return OFFSET;
    }

    @Override
    public void tick() {
        clearDraggedMotion();
        resolveAttachedEntity();
        super.tick();
        if (!isAlive()) {
            return;
        }

        LivingEntity attached = getEntityAttachedTo();
        if (attached == null) {
            return;
        }

        LivingEntity owner = getOwner();
        if (!attached.isAlive() || owner == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        Vec3 toOwner = owner.position().subtract(attached.position());
        if (toOwner.lengthSqr() <= PULL_STOP_DISTANCE_SQR) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        if (!level().isClientSide()) {
            dragTarget(attached, toOwner.normalize());
            attached.fallDistance = 0.0F;
        }
    }

    @Override
    protected HitResult[] rayTrace() {
        return entityData.get(COLLISION_RESOLVED)
                ? new HitResult[0]
                : super.rayTrace();
    }

    @Override
    protected void moveProjectile() {
        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            setPos(
                    attached.getX(),
                    attached.getY(0.5D),
                    attached.getZ());
            setDeltaMovement(Vec3.ZERO);
            return;
        }

        LivingEntity owner = getOwner();
        if (owner == null) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        setRot(owner.getYRot(), owner.getXRot());
        double nextDistance = updateDistance();
        updateMotionFlags();
        if (isRetracting() && nextDistance <= 0.0D) {
            if (!level().isClientSide()) {
                discard();
            }
            return;
        }

        distance = Math.max(0.0D, nextDistance);
        Vec3 origin = ownerPosition(1.0F, false);
        Vec3 next = origin.add(
                Vec3.directionFromRotation(
                                owner.getXRot(), owner.getYRot())
                        .scale(distance));
        Vec3 current = position();
        setDeltaMovement(next.subtract(current));
        xo = current.x;
        yo = current.y;
        zo = current.z;
        xOld = current.x;
        yOld = current.y;
        zOld = current.z;
        setPos(next);
    }

    private double updateDistance() {
        if (isRetracting()) {
            return distance - retractSpeed() * speedFactor;
        }
        if (movingForward) {
            return distance + movementSpeed() * speedFactor;
        }
        return distance;
    }

    private void updateMotionFlags() {
        int forwardTicks = Math.max(
                1, (int) (maxDistance() / movementSpeed()));
        if (movingForward && tickCount >= forwardTicks) {
            movingForward = false;
        }
        if (!isRetracting()
                && tickCount >= forwardTicks + timeAtFullLength()) {
            setRetracting(true);
        }
    }

    private double maxDistance() {
        return movementSpeed()
                * retractSpeed()
                * (ticksLifespan() - timeAtFullLength())
                / (movementSpeed() + retractSpeed());
    }

    private double movementSpeed() {
        return 0.3D;
    }

    private int timeAtFullLength() {
        return 1;
    }

    private double retractSpeed() {
        return 1.5D;
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (getEntityAttachedTo() == null
                && pullsTargets()
                && target instanceof LivingEntity livingTarget
                && !JojoModUtil.isTargetBlocking(livingTarget)) {
            // 1.16 catch cue here, not in attachToEntity: reload re-attach stays silent
            attachToEntity(livingTarget);
            playSound(ModSoundEvents.HIEROPHANT_GREEN_GRAPPLE_CATCH.get(),
                    1.0F, 1.0F);
        }
        return super.hurtTarget(target, owner);
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hitResult, boolean entityHurt) {
        entityData.set(COLLISION_RESOLVED, true);
        setRetracting(true);
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult hitResult, boolean blockDestroyed) {
        entityData.set(COLLISION_RESOLVED, true);
        setRetracting(true);
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (!level().isClientSide()) {
            setRetracting(true);
        }
    }

    @Nullable
    public LivingEntity getEntityAttachedTo() {
        int entityId = entityData.get(ATTACHED_ENTITY);
        if (entityId < 0) {
            return null;
        }
        Entity entity = level().getEntity(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    private void attachToEntity(LivingEntity target) {
        attachedEntityUuid = null;
        entityData.set(ATTACHED_ENTITY, target.getId());
        entityData.set(COLLISION_RESOLVED, true);
        updateAttachedPosition(target);
        setDeltaMovement(Vec3.ZERO);
        if (getOwner() instanceof StandEntity stand) {
            stand.getManualMovementLocks().addLock(
                    MANUAL_MOVEMENT_LOCK,
                    InputDirection.values());
        }
    }

    private void resolveAttachedEntity() {
        if (!level().isClientSide()
                && attachedEntityUuid != null
                && entityData.get(ATTACHED_ENTITY) < 0
                && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(attachedEntityUuid);
            if (entity instanceof LivingEntity living) {
                attachToEntity(living);
                attachedEntityUuid = null;
            }
        }
    }

    private void updateAttachedPosition(LivingEntity target) {
        setPos(
                target.getX(),
                target.getY(0.5D),
                target.getZ());
    }

    private void dragTarget(Entity target, Vec3 impulse) {
        Entity root = target.getRootVehicle();
        doDragEntity(root, impulse);
        if (root instanceof StandEntity stand) {
            LivingEntity user = stand.getUser();
            if (user != null) {
                doDragEntity(user.getRootVehicle(), impulse);
            }
        }
    }

    private void doDragEntity(Entity entity, Vec3 impulse) {
        if (entity instanceof LivingEntity living) {
            for (Holder<MobEffect> effect
                    : living.getActiveEffectsMap().keySet()) {
                if (effect.is(ModStatusEffects.IMMOBILIZE)
                        && effect.value()
                                instanceof ImmobilizeEffect immobilize
                        && immobilize.resetsDeltaMovement()) {
                    entity.move(MoverType.PLAYER, impulse);
                    return;
                }
            }
        }
        entity.setDeltaMovement(impulse);
        draggedEntities.add(entity);
        entity.fallDistance = 0.0F;
        entity.hurtMarked = true;
    }

    private void clearDraggedMotion() {
        draggedEntities.forEach(
                entity -> entity.setDeltaMovement(Vec3.ZERO));
        draggedEntities.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        clearDraggedMotion();
        if (getOwner() instanceof StandEntity stand) {
            stand.getManualMovementLocks().removeLock(
                    MANUAL_MOVEMENT_LOCK);
        }
        super.remove(reason);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeVarInt(lifeSpan);
        buffer.writeDouble(distance);
        buffer.writeBoolean(movingForward);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        lifeSpan = buffer.readVarInt();
        distance = buffer.readDouble();
        movingForward = buffer.readBoolean();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putInt("LifeSpan", lifeSpan);
        nbt.putDouble("Distance", distance);
        nbt.putBoolean("MovingForward", movingForward);
        nbt.putBoolean("PullTargets", pullsTargets());
        nbt.putBoolean("Retracting", isRetracting());
        nbt.putBoolean(
                "CollisionResolved",
                entityData.get(COLLISION_RESOLVED));
        LivingEntity attached = getEntityAttachedTo();
        if (attached != null) {
            nbt.putUUID("AttachedEntity", attached.getUUID());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        lifeSpan = nbt.contains("LifeSpan")
                ? Math.max(1, nbt.getInt("LifeSpan"))
                : lifeSpan;
        distance = nbt.getDouble("Distance");
        movingForward = !nbt.contains("MovingForward")
                || nbt.getBoolean("MovingForward");
        setPullTargets(nbt.getBoolean("PullTargets"));
        setRetracting(nbt.getBoolean("Retracting"));
        entityData.set(
                COLLISION_RESOLVED,
                nbt.getBoolean("CollisionResolved"));
        attachedEntityUuid = nbt.hasUUID("AttachedEntity")
                ? nbt.getUUID("AttachedEntity")
                : null;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ATTACHED_ENTITY, -1);
        builder.define(PULL_TARGETS, false);
        builder.define(RETRACTING, false);
        builder.define(COLLISION_RESOLVED, false);
    }
}

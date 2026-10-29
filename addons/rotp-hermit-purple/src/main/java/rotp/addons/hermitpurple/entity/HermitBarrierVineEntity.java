package rotp.addons.hermitpurple.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.hermitpurple.HermitHamon;
import rotp.addons.hermitpurple.HermitPurpleAddon;
import rotp.addons.hermitpurple.HermitPurpleSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HermitBarrierVineEntity
        extends OwnerBoundProjectileEntity {
    private static final EntityDataAccessor<BlockPos> ORIGIN =
            SynchedEntityData.defineId(
                    HermitBarrierVineEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> ATTACHED =
            SynchedEntityData.defineId(
                    HermitBarrierVineEntity.class,
                    EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<BlockPos> TERMINAL =
            SynchedEntityData.defineId(
                    HermitBarrierVineEntity.class,
                    EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> RIPPED =
            SynchedEntityData.defineId(
                    HermitBarrierVineEntity.class,
                    EntityDataSerializers.BOOLEAN);

    private int rippedTicks = -1;

    public HermitBarrierVineEntity(
            LivingEntity shooter, Level level) {
        super(
                HermitPurpleAddon.HP_BARRIER_VINE_ENTITY.get(),
                shooter,
                level);
    }

    public HermitBarrierVineEntity(
            EntityType<? extends HermitBarrierVineEntity> type,
            Level level) {
        super(type, level);
    }

    public void setOriginBlockPos(BlockPos blockPos) {
        entityData.set(ORIGIN, blockPos.immutable());
    }

    public BlockPos getOriginBlockPos() {
        return entityData.get(ORIGIN);
    }

    public void attachToBlock(BlockPos blockPos) {
        entityData.set(TERMINAL, blockPos.immutable());
        entityData.set(ATTACHED, true);
        setPos(Vec3.atCenterOf(blockPos));
        setDeltaMovement(Vec3.ZERO);
    }

    public boolean isAttachedToBlock() {
        return entityData.get(ATTACHED);
    }

    public boolean wasRipped() {
        return entityData.get(RIPPED);
    }

    @Override
    public Vec3 getOriginPoint(float partialTick) {
        return Vec3.atCenterOf(getOriginBlockPos());
    }

    @Override
    protected boolean shouldFollowOwner() {
        return !isAttachedToBlock();
    }

    @Override
    protected Vec3 ownerOffset() {
        return new Vec3(0.15D, -1.4D, 0.0D);
    }

    @Override
    protected void moveProjectile() {
        if (isAttachedToBlock()) {
            setPos(Vec3.atCenterOf(entityData.get(TERMINAL)));
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.moveProjectile();
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) {
            return;
        }
        if (wasRipped()) {
            if (rippedTicks < 0) {
                rippedTicks =
                        HermitPurpleSpec.BARRIER_RIPPED_TICKS;
            }
            if (!level().isClientSide() && --rippedTicks <= 0) {
                discard();
            }
            return;
        }
        if (!level().isClientSide()) {
            LivingEntity owner = getOwner();
            LivingEntity user =
                    owner instanceof StandEntity stand
                            ? stand.getUser()
                            : owner;
            if (!(owner instanceof HermitPurpleStandEntity)
                    || user == null
                    || !HermitPurpleAddon.isHermitPurpleUser(user)
                    || user.distanceToSqr(this)
                            > HermitPurpleSpec.BARRIER_RANGE
                            * HermitPurpleSpec.BARRIER_RANGE
                    || !level().hasChunkAt(blockPosition())) {
                discard();
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (wasRipped()
                || !isAttachedToBlock()
                || !(entity instanceof LivingEntity)
                || !super.canHitEntity(entity)) {
            return false;
        }
        LivingEntity owner = getOwner();
        if (entity == owner) {
            return false;
        }
        if (owner instanceof StandEntity stand) {
            return entity != stand.getUser()
                    && stand.canAttackEntity(entity);
        }
        return true;
    }

    @Override
    protected void afterEntityHit(
            EntityHitResult hit, boolean entityHurt) {
        // 1.16 HPVineBarrierEntity.onHitEntity rips, stops the target and
        // deals the Hamon hit whatever the plain hit returned.
        if (level().isClientSide() || wasRipped()) {
            return;
        }
        Entity target = hit.getEntity();
        target.setDeltaMovement(Vec3.ZERO);
        LivingEntity owner = getOwner();
        LivingEntity user =
                owner instanceof StandEntity stand
                        ? stand.getUser()
                        : owner;
        if (target instanceof LivingEntity living && user != null) {
            HermitHamon.hurt(
                    living,
                    user,
                    HermitPurpleSpec.BARRIER_HAMON_DAMAGE,
                    false);
        }
        entityData.set(RIPPED, true);
        rippedTicks = HermitPurpleSpec.BARRIER_RIPPED_TICKS;
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, @Nullable HitResult target) {}

    @Override
    public int ticksLifespan() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected float getBaseDamage() {
        return HermitPurpleSpec.BARRIER_DAMAGE;
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
    public boolean isBodyPart() {
        return true;
    }

    @Override
    public boolean ownerInvisibility() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (getOwner()
                instanceof HermitPurpleStandEntity controller) {
            controller.forgetBarrier(this);
        }
        super.remove(reason);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ORIGIN, BlockPos.ZERO);
        builder.define(ATTACHED, false);
        builder.define(TERMINAL, BlockPos.ZERO);
        builder.define(RIPPED, false);
    }
}

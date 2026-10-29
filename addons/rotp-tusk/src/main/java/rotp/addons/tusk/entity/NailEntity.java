package rotp.addons.tusk.entity;

import java.util.LinkedList;
import java.util.List;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskActRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class NailEntity extends ModdedProjectileEntity {
    public static final float BASE_DAMAGE = 4.0F;
    public static final float MAX_HARDNESS = 0.5F;
    public static final int LIFESPAN_TICKS = 360;
    public static final double GRAVITY_ACCELERATION = 1.5D;

    private static final EntityDataAccessor<Integer> SPIN_CHARGE =
            SynchedEntityData.defineId(
                    NailEntity.class, EntityDataSerializers.INT);

    public final List<Vec3> tracePos = new LinkedList<>();
    public Vec3 initialPos;

    public NailEntity(
            EntityType<? extends NailEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
    }

    public NailEntity(LivingEntity shooter, Level level) {
        super(TuskAddon.NAIL_ENTITY.get(), shooter, level);
        setSpinCharge(0);
    }

    public int getSpinCharge() {
        return entityData.get(SPIN_CHARGE);
    }

    public void setSpinCharge(int spinCharge) {
        entityData.set(SPIN_CHARGE, Math.max(0, spinCharge));
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount == 5) {
            setNoGravity(false);
        }
        if (level().isClientSide() && !isRemoved()) {
            Vec3 pos = position();
            boolean addPos = tracePos.isEmpty()
                    || pos.distanceToSqr(
                            tracePos.get(tracePos.size() - 1)) >= 0.0625D;
            if (addPos) {
                tracePos.add(pos);
            }
        }
    }

    @Override
    protected float getBaseDamage() {
        return BASE_DAMAGE + getSpinCharge();
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return MAX_HARDNESS;
    }

    @Override
    public boolean standDamage() {
        return true;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
    }

    @Override
    protected boolean hasGravity() {
        return false;
    }

    // 1.16 kept the core's constVelocity() == true, so this gravity and the tick-5 switch in tick() never apply.
    @Override
    protected double getGravityAcceleration() {
        return GRAVITY_ACCELERATION;
    }

    @Override
    public boolean hasDeflectedVisuals() {
        return true;
    }

    @Override
    public void setIsDeflected(Vec3 deflectVec, Vec3 deflectPos) {
        super.setIsDeflected(deflectVec, deflectPos);
        if (level().isClientSide() && deflectVec != null) {
            setDeltaMovement(deflectVec);
            if (deflectPos != null) {
                tracePos.add(deflectPos);
            }
        }
    }

    // 1.16: a broken soft block only lets the rest of this tick's path be checked; the nail still breaks below.
    @Override
    protected void afterBlockHit(
            BlockHitResult blockHitResult, boolean blockDestroyed) {
        if (blockDestroyed && !level().isClientSide()) {
            setDeltaMovement(getDeltaMovement().scale(0.9D));
            checkHit();
        }
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (!level().isClientSide()
                && getSpinCharge() > 0
                && getOwner() != null) {
            LivingEntity target = hitTarget
                    instanceof EntityHitResult entityHit
                    && entityHit.getEntity()
                            instanceof LivingEntity living
                    ? living
                    : null;
            WormholeEntity wormhole = new WormholeEntity(
                    getOwner(),
                    level(),
                    TuskActRules.wormholeLifetimeForSpinCharge(
                            getSpinCharge()),
                    target);
            wormhole.setPos(position());
            level().addFreshEntity(wormhole);
        }
        super.breakProjectile(targetType, hitTarget);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SPIN_CHARGE, 0);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        initialPos = position();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SpinCharge", getSpinCharge());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSpinCharge(tag.getInt("SpinCharge"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}

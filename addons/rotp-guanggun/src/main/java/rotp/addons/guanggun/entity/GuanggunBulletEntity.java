package rotp.addons.guanggun.entity;

import java.util.LinkedList;
import java.util.List;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.guanggun.GuanggunAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GuanggunBulletEntity extends ModdedProjectileEntity {
    public static final float BASE_DAMAGE = 4.0F;
    public static final float MAX_HARDNESS = 0.5F;
    public static final int LIFESPAN_TICKS = 360;
    public static final double GRAVITY_ACCELERATION = 1.5D;
    public static final int MAX_TRACE_POINTS = 20;

    public final List<Vec3> tracePos = new LinkedList<>();
    public Vec3 initialPos;
    private boolean blockDestroyed;

    public GuanggunBulletEntity(
            EntityType<? extends GuanggunBulletEntity> type,
            Level level) {
        super(type, level);
    }

    public GuanggunBulletEntity(
            LivingEntity shooter, Level level) {
        super(GuanggunAddon.GUANGGUN_BULLET.get(), shooter, level);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() && !isRemoved()) {
            Vec3 pos = position();
            if (tracePos.isEmpty()
                    || pos.distanceToSqr(
                            tracePos.get(tracePos.size() - 1)) > 0.0625D) {
                tracePos.add(pos);
            }
            while (tracePos.size() > MAX_TRACE_POINTS) {
                tracePos.remove(0);
            }
        }
    }

    @Override
    protected boolean constVelocity() {
        return false;
    }

    @Override
    protected boolean hasGravity() {
        return true;
    }

    @Override
    protected double getGravityAcceleration() {
        return GRAVITY_ACCELERATION;
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
        return true;
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
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

    @Override
    protected void afterBlockHit(
            BlockHitResult hit, boolean destroyed) {
        blockDestroyed = destroyed;
        if (destroyed && !level().isClientSide()) {
            setDeltaMovement(getDeltaMovement().scale(0.9D));
            checkHit();
        }
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (targetType != TargetType.BLOCK || !blockDestroyed) {
            super.breakProjectile(targetType, hitTarget);
        }
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        super.readSpawnData(buffer);
        initialPos = position();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }
}

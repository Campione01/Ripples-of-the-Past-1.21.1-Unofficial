package rotp.addons.d4c.entity;

import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class D4CColtBulletEntity extends TommyGunBulletEntity {
    public D4CColtBulletEntity(
            EntityType<? extends D4CColtBulletEntity> type,
            Level level) {
        super(type, level);
    }

    public D4CColtBulletEntity(LivingEntity shooter, Level level) {
        super(D4CAddon.COLT_BULLET.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
        setNoGravity(false);
    }

    @Override
    public int ticksLifespan() {
        return 60;
    }

    @Override
    protected float getBaseDamage() {
        return 10.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.8F;
    }

    @Override
    protected double getGravityAcceleration() {
        return 1.2D;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    public boolean hasDeflectedVisuals() {
        return true;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult result, boolean blockDestroyed) {
        if (blockDestroyed) {
            if (!level().isClientSide()) {
                setDeltaMovement(getDeltaMovement().scale(0.2D));
                checkHit();
            }
            return;
        }
        BlockPos pos = result.getBlockPos();
        BlockState state = level().getBlockState(pos);
        SoundType sound = state.getSoundType(level(), pos, this);
        level().playSound(
                null,
                pos,
                sound.getBreakSound(),
                SoundSource.BLOCKS,
                1.0F,
                sound.getPitch() * 0.5F);
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, HitResult hitTarget) {
        if (targetType != TargetType.BLOCK) {
            super.breakProjectile(targetType, hitTarget);
        }
    }
}

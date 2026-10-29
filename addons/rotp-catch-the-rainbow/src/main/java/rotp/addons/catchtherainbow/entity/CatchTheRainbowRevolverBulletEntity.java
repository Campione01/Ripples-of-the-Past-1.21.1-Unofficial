package rotp.addons.catchtherainbow.entity;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class CatchTheRainbowRevolverBulletEntity
        extends ModdedProjectileEntity {
    public CatchTheRainbowRevolverBulletEntity(
            LivingEntity shooter, Level level) {
        super(
                ModEntityTypes.TOMMY_GUN_BULLET.get(),
                shooter,
                level);
    }

    @Override
    public int ticksLifespan() {
        return 100;
    }

    @Override
    protected float getBaseDamage() {
        return 4.0F;
    }

    @Override
    protected float getMaxHardnessBreakable() {
        return 0.3F;
    }

    @Override
    public boolean standDamage() {
        return false;
    }

    @Override
    protected boolean hasGravity() {
        return true;
    }

    @Override
    protected boolean constVelocity() {
        return false;
    }

    @Override
    protected double getGravityAcceleration() {
        return 1.5D;
    }

    @Override
    protected void afterBlockHit(
            BlockHitResult result,
            boolean blockDestroyed) {
        if (blockDestroyed) {
            if (!level().isClientSide()) {
                setDeltaMovement(
                        getDeltaMovement().scale(0.9D));
                checkHit();
            }
            return;
        }
        BlockPos pos = result.getBlockPos();
        BlockState state = level().getBlockState(pos);
        SoundType sound =
                state.getSoundType(level(), pos, this);
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

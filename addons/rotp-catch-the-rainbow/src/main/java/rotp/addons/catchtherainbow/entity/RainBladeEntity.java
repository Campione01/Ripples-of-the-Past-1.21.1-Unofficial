package rotp.addons.catchtherainbow.entity;

import javax.annotation.Nullable;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.subsystems.target.ActionTarget.TargetType;

import rotp.addons.catchtherainbow.CatchTheRainbowAddon;
import rotp.addons.catchtherainbow.util.RainExposure;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public final class RainBladeEntity extends ModdedProjectileEntity {
    public static final int LIFESPAN_TICKS = 100;
    public static final float BASE_DAMAGE = 8.0F;

    public RainBladeEntity(
            EntityType<? extends RainBladeEntity> entityType,
            Level level) {
        super(entityType, level);
    }

    public RainBladeEntity(
            LivingEntity shooter, Level level, int variant) {
        super(typeFor(variant), shooter, level);
    }

    private static EntityType<RainBladeEntity> typeFor(
            int variant) {
        return switch (variant) {
            case 2 -> CatchTheRainbowAddon
                    .RAIN_BLADE_ENTITY_2.get();
            case 3 -> CatchTheRainbowAddon
                    .RAIN_BLADE_ENTITY_3.get();
            default -> CatchTheRainbowAddon
                    .RAIN_BLADE_ENTITY_1.get();
        };
    }

    @Override
    protected boolean hurtTarget(
            Entity target, @Nullable LivingEntity owner) {
        if (level().isClientSide()
                || !RainExposure.isExposedToRain(target)) {
            return false;
        }
        return super.hurtTarget(target, owner);
    }

    @Override
    protected void breakProjectile(
            TargetType targetType, @Nullable HitResult hitTarget) {
        if (targetType != TargetType.ENTITY
                || hitTarget instanceof EntityHitResult entityHit
                        && entityHit.getEntity()
                                instanceof LivingEntity) {
            super.breakProjectile(targetType, hitTarget);
        }
    }

    @Override
    public int ticksLifespan() {
        return LIFESPAN_TICKS;
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
        return false;
    }
}

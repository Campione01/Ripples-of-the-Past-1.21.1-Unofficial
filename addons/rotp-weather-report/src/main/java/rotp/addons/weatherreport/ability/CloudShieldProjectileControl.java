package rotp.addons.weatherreport.ability;

import java.util.List;

import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

public final class CloudShieldProjectileControl {
    public static final double HELD_SHIELD_RANGE = 2.5D;
    public static final double GRANTED_SHIELD_RANGE = 3.5D;

    private CloudShieldProjectileControl() {}

    public static void redirectHeldShield(
            ServerLevel level,
            LivingEntity user,
            StandEntity stand) {
        Vec3 look = stand.getLookAngle();
        Vec3 redirected = new Vec3(
                look.x * 0.5D,
                0.1D,
                look.z * 0.5D);
        projectilesAround(
                level,
                user,
                HELD_SHIELD_RANGE,
                true).forEach(projectile ->
                        redirect(level, projectile, redirected));
    }

    public static void redirectGrantedShield(
            ServerLevel level,
            LivingEntity protectedEntity) {
        projectilesAround(
                level,
                protectedEntity,
                GRANTED_SHIELD_RANGE,
                false).forEach(projectile -> {
                    Vec3 velocity = projectile.getDeltaMovement();
                    redirect(
                            level,
                            projectile,
                            new Vec3(
                                    -velocity.x * 0.5D,
                                    1.0D,
                                    -velocity.z * 0.5D));
                });
    }

    private static List<Projectile> projectilesAround(
            ServerLevel level,
            LivingEntity center,
            double range,
            boolean excludePickable) {
        return level.getEntitiesOfClass(
                Projectile.class,
                center.getBoundingBox().inflate(range),
                projectile -> projectile.isAlive()
                        && !(projectile
                                instanceof OwnerBoundProjectileEntity)
                        && (!excludePickable
                                || !projectile.isPickable()));
    }

    private static void redirect(
            ServerLevel level,
            Projectile projectile,
            Vec3 velocity) {
        projectile.setDeltaMovement(velocity);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        level.sendParticles(
                ParticleTypes.CLOUD,
                projectile.getX(),
                projectile.getY(0.5D),
                projectile.getZ(),
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
    }
}

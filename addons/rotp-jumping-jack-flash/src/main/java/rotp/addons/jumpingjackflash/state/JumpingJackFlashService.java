package rotp.addons.jumpingjackflash.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.init.ModSoundEvents;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.entity.GravityDomainEntity;
import rotp.addons.jumpingjackflash.entity.JumpingJackFlashStandEntity;
import rotp.addons.jumpingjackflash.entity.TrackingBulletEntity;
import rotp.addons.jumpingjackflash.entity.TrackingSnowballEntity;
import rotp.addons.jumpingjackflash.state.GravityLeaseService.Source;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class JumpingJackFlashService {
    public static final float VOLLEY_SPREAD_DEGREES = 4.0F;
    public static final float VOLLEY_SPEED = 3.0F;
    public static final double SNOWBALL_TARGET_RANGE = 20.0D;

    private JumpingJackFlashService() {}

    public static void fireVolley(ServerPlayer player) {
        JumpingJackFlashStandEntity stand =
                JumpingJackFlashAddon.activeStand(player);
        if (stand == null) {
            return;
        }
        Vec3 look = player.getLookAngle();
        for (int index = 0;
                index < TrackingBulletEntity.VOLLEY_COUNT;
                index++) {
            float yaw = (stand.getRandom().nextFloat() - 0.5F)
                    * VOLLEY_SPREAD_DEGREES * 2.0F;
            float pitch = (stand.getRandom().nextFloat() - 0.5F)
                    * VOLLEY_SPREAD_DEGREES * 2.0F;
            Vec3 direction = rotate(look, yaw, pitch).normalize();
            TrackingBulletEntity bullet =
                    new TrackingBulletEntity(player, stand.level());
            bullet.setPos(
                    stand.getX(),
                    stand.getEyeY() - 0.2D,
                    stand.getZ());
            bullet.shoot(
                    direction.x,
                    direction.y,
                    direction.z,
                    VOLLEY_SPEED,
                    0.0F);
            stand.level().addFreshEntity(bullet);
        }
        stand.playSound(
                ModSoundEvents.STAND_PUNCH_LIGHT.get(),
                1.0F,
                1.1F);
    }

    public static void throwGravitySnowball(ServerPlayer player) {
        JumpingJackFlashStandEntity stand =
                JumpingJackFlashAddon.activeStand(player);
        if (stand == null) {
            return;
        }
        ActionTarget aimed = HitResultUtil.clip(
                player.getEyePosition(),
                player.getLookAngle(),
                SNOWBALL_TARGET_RANGE,
                SNOWBALL_TARGET_RANGE,
                player.level(),
                entity -> entity instanceof LivingEntity living
                        && living.isAlive()
                        && living != player
                        && living != stand,
                player,
                0.0D);
        Entity aimedEntity = aimed.getMainEntity();
        LivingEntity target = aimedEntity instanceof LivingEntity living
                ? living : null;

        TrackingSnowballEntity snowball =
                new TrackingSnowballEntity(
                        JumpingJackFlashAddon.TRACKING_SNOWBALL.get(),
                        player.level());
        snowball.setOwner(player);
        snowball.setTarget(target);
        Vec3 origin = stand.getEyePosition().add(
                player.getLookAngle().scale(0.25D));
        snowball.setPos(origin.x, origin.y, origin.z);
        snowball.shootFromRotation(
                player,
                player.getXRot(),
                player.getYRot(),
                0.0F,
                TrackingSnowballEntity.SPEED,
                1.0F);
        player.level().addFreshEntity(snowball);
    }

    public static void cancelGravity(ServerPlayer player) {
        GravityLeaseService.releaseOwnerSource(
                player.getServer(),
                player.getUUID(),
                Source.SNOWBALL);
    }

    public static boolean openDomain(ServerPlayer player) {
        if (hasDomain(player)
                || !JumpingJackFlashAddon.isUser(player)) {
            return false;
        }
        GravityDomainEntity domain = new GravityDomainEntity(
                JumpingJackFlashAddon.GRAVITY_DOMAIN.get(),
                player.serverLevel());
        domain.initialize(player);
        player.serverLevel().addFreshEntity(domain);
        return true;
    }

    public static boolean closeDomain(ServerPlayer player) {
        GravityDomainEntity domain = findDomain(
                player.getServer(), player.getUUID());
        if (domain == null || domain.isClosing()) {
            return false;
        }
        domain.pullAndClose();
        return true;
    }

    public static boolean hasDomain(ServerPlayer player) {
        return findDomain(player.getServer(), player.getUUID()) != null;
    }

    public static void cleanupOwner(ServerPlayer player) {
        cleanupOwner(player.getServer(), player.getUUID());
    }

    public static void cleanupOwner(
            MinecraftServer server, UUID owner) {
        List<GravityDomainEntity> domains = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof GravityDomainEntity domain
                        && owner.equals(domain.ownerUuid())) {
                    domains.add(domain);
                }
            }
        }
        domains.forEach(GravityDomainEntity::terminate);
        GravityLeaseService.releaseOwner(server, owner);
    }

    private static GravityDomainEntity findDomain(
            MinecraftServer server, UUID owner) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof GravityDomainEntity domain
                        && domain.isAlive()
                        && !domain.isRemoved()
                        && owner.equals(domain.ownerUuid())) {
                    return domain;
                }
            }
        }
        return null;
    }

    private static Vec3 rotate(
            Vec3 vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double x = vector.x * cosYaw - vector.z * sinYaw;
        double z = vector.x * sinYaw + vector.z * cosYaw;
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double y = vector.y * cosPitch - z * sinPitch;
        z = vector.y * sinPitch + z * cosPitch;
        return new Vec3(x, y, z);
    }
}

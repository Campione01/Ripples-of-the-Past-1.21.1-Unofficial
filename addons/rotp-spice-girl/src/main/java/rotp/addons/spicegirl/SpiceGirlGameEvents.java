package rotp.addons.spicegirl;

import rotp.addons.spicegirl.state.ElasticTargetState;
import rotp.addons.spicegirl.state.SpiceGirlElasticService;
import rotp.addons.spicegirl.state.SpiceGirlSoftAreaService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = SpiceGirlAddon.MOD_ID)
public final class SpiceGirlGameEvents {
    public static final double MIN_FALL_BOUNCE = 0.6D;
    public static final double MAX_FALL_BOUNCE = 1.5D;
    public static final double PROJECTILE_REFLECTION_SPEED = 0.5D;
    public static final float MAX_KNOCKBACK_MULTIPLIER = 2.0F;

    private SpiceGirlGameEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof LivingEntity living) {
            if (living.hasData(
                    SpiceGirlAddon.ELASTIC_TARGET)) {
                SpiceGirlElasticService.tickTarget(living);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer owner) {
            SpiceGirlElasticService.validateOwner(owner);
            SpiceGirlSoftAreaService.validate(owner);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.hasData(SpiceGirlAddon.SOFT_AREA_INDEX)) {
            SpiceGirlAddon.softAreaIndex(level)
                    .pruneExpired(level.getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        boolean elastic =
                SpiceGirlElasticService.isElastic(entity);
        boolean softArea = SpiceGirlSoftAreaService.isSoftArea(
                level, entity.blockPosition())
                || SpiceGirlSoftAreaService.isSoftArea(
                        level, entity.blockPosition().below());
        if (!elastic && !softArea) {
            return;
        }

        double bounce = Math.min(
                MAX_FALL_BOUNCE,
                Math.max(
                        MIN_FALL_BOUNCE,
                        event.getDistance() * 0.08D));
        Vec3 movement = entity.getDeltaMovement();
        entity.setDeltaMovement(
                movement.x, bounce, movement.z);
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingKnockBack(
            LivingKnockBackEvent event) {
        ElasticTargetState state =
                SpiceGirlElasticService.validate(
                        event.getEntity());
        if (state == null) {
            return;
        }
        float multiplier =
                1.5F + 0.25F * state.amplifier();
        float capped = Math.min(
                event.getOriginalStrength()
                        * MAX_KNOCKBACK_MULTIPLIER,
                event.getStrength() * multiplier);
        event.setStrength(capped);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult hit)
                || !(hit.getEntity()
                        instanceof LivingEntity target)
                || !SpiceGirlElasticService.isElastic(target)) {
            return;
        }

        Vec3 movement = projectile.getDeltaMovement();
        Vec3 reflected = movement.lengthSqr() > 1.0E-8D
                ? movement.reverse()
                        .normalize()
                        .scale(PROJECTILE_REFLECTION_SPEED)
                : Vec3.ZERO;
        projectile.setDeltaMovement(reflected);
        projectile.setYRot(
                (projectile.getYRot() + 180.0F) % 360.0F);
        projectile.setOwner(target);
        projectile.hasImpulse = true;
        if (projectile.level()
                instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.ITEM_SLIME,
                    projectile.getX(),
                    projectile.getY(),
                    projectile.getZ(),
                    4,
                    0.1D,
                    0.1D,
                    0.1D,
                    0.02D);
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()
                || !SpiceGirlElasticService.isElastic(entity)) {
            return;
        }
        if (event.getSource().getDirectEntity()
                instanceof Projectile) {
            event.setCanceled(true);
            return;
        }
        if (entity instanceof ServerPlayer player
                && event.getSource().is(
                        DamageTypes.FLY_INTO_WALL)) {
            event.setCanceled(true);
            if (player.isFallFlying()) {
                player.setDeltaMovement(
                        player.getLookAngle().scale(-2.0D));
                player.hurtMarked = true;
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(
            BlockEvent.BreakEvent event) {
        if (event.getLevel()
                instanceof ServerLevel level) {
            SpiceGirlSoftAreaService.removeBlock(
                    level, event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(
            LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        SpiceGirlElasticService.clearTarget(entity);
        if (entity instanceof ServerPlayer owner) {
            clearOwner(owner);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(
            PlayerEvent.Clone event) {
        if (event.getOriginal()
                instanceof ServerPlayer original) {
            clearOwner(original);
            SpiceGirlElasticService.clearTarget(original);
        }
        if (event.getEntity()
                instanceof ServerPlayer player) {
            clearOwner(player);
            SpiceGirlElasticService.clearTarget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            clearOwner(player);
            SpiceGirlElasticService.clearTarget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            clearOwner(player);
            SpiceGirlElasticService.clearTarget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity()
                instanceof ServerPlayer player) {
            clearOwner(player);
            SpiceGirlElasticService.clearTarget(player);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity()
                instanceof LivingEntity living) {
            SpiceGirlElasticService.clearTarget(living);
        }
    }

    private static void clearOwner(ServerPlayer owner) {
        SpiceGirlElasticService.clearOwner(owner);
        SpiceGirlSoftAreaService.clearOwner(owner);
    }
}

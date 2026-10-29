package rotp.addons.ultimate_lifeform.power;

import javax.annotation.Nullable;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 UltimateLifeformLiquidWalkingMixin and UltimateLifeformLiquidWalking: an Ultimate Lifeform with Hamon charge
 * stands on the surface of any fluid, called from Entity.move after collide (EntityUltimateLiquidWalkingMixin).
 */
public final class UltimateLiquidWalking {
    // 1.16 waterWalkingTickCost(): waterWalkingPrevTick was never set (setWaterWalkingThisTick has no caller)
    public static final int TICK_COST = 50;
    // 1.16 kept one static counter for every caller: every entity and both logical sides of an integrated server
    private static int tickCount;

    private UltimateLiquidWalking() {}

    public static Vec3 fluidCollision(LivingEntity entity, Vec3 originalDisplacement) {
        if (originalDisplacement.y <= 0.0D
                && !isTouchingFluid(entity, entity.getBoundingBox().deflate(0.001D))) {
            Double highestDistance = findHighestFluidDistance(entity, originalDisplacement);
            if (highestDistance != null) {
                Vec3 finalDisplacement = new Vec3(
                        originalDisplacement.x, highestDistance, originalDisplacement.z);
                AABB finalBox = entity.getBoundingBox().move(finalDisplacement).deflate(0.001D);
                if (isTouchingFluid(entity, finalBox)) {
                    return originalDisplacement;
                }
                entity.fallDistance = 0.0F;
                entity.setOnGround(true);
                return finalDisplacement;
            }
        }
        return originalDisplacement;
    }

    @Nullable
    private static Double findHighestFluidDistance(LivingEntity entity, Vec3 originalDisplacement) {
        AABB box = entity.getBoundingBox().move(originalDisplacement);
        Vec3[] points = {
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ)};
        double fluidStepHeight = entity.onGround() ? Math.max(1.0D, entity.maxUpStep()) : 0.0D;
        Double highestDistance = null;
        for (Vec3 point : points) {
            BlockPos corner = BlockPos.containing(point);
            for (int i = 0; ; --i) {
                // 1.16 new BlockPos(corner).add(0, i + fluidStepHeight, 0)
                BlockPos landingPos = BlockPos.containing(
                        corner.getX(), corner.getY() + i + fluidStepHeight, corner.getZ());
                FluidState landingState = entity.level().getFluidState(landingPos);
                double distanceToFluidSurface =
                        landingPos.getY() + landingState.getOwnHeight() - entity.getY();
                if (distanceToFluidSurface < originalDisplacement.y
                        || distanceToFluidSurface > fluidStepHeight) {
                    break;
                }
                if (!landingState.isEmpty() && onLiquidWalkingEvent(entity, landingState)) {
                    if (highestDistance == null || distanceToFluidSurface > highestDistance) {
                        highestDistance = distanceToFluidSurface;
                    }
                    break;
                }
            }
        }
        return highestDistance;
    }

    private static boolean isTouchingFluid(LivingEntity entity, AABB box) {
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.ceil(box.maxY);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);
        Level level = entity.level();
        if (level.hasChunksAt(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int x = minX; x < maxX; ++x) {
                for (int y = minY; y < maxY; ++y) {
                    for (int z = minZ; z < maxZ; ++z) {
                        mutable.set(x, y, z);
                        FluidState fluidState = level.getFluidState(mutable);
                        if (!fluidState.isEmpty() && fluidState.getHeight(level, mutable) + y >= box.minY) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean onLiquidWalkingEvent(LivingEntity entity, FluidState fluidState) {
        ++tickCount;
        UltimateLifeformData data = PlayerPower.getPowerData(entity, UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        boolean liquidWalking = data != null && isLiquidWalking(entity, data, fluidState);
        if (liquidWalking && entity.isEffectiveAi() && tickCount >= 10) {
            emitSparks(entity);
            tickCount = 0;
        }
        return liquidWalking;
    }

    private static boolean isLiquidWalking(LivingEntity entity, UltimateLifeformData data, FluidState fluidState) {
        if (entity.isShiftKeyDown() && data.getDoubleShiftPress()) {
            return false;
        }
        if (fluidState.is(FluidTags.WATER) && entity.isOnFire() || data.getHamonCharge() <= 0) {
            return false;
        }
        entity.setOnGround(true);
        if (!entity.level().isClientSide() && fluidState.is(FluidTags.LAVA)
                && !entity.fireImmune() && !hasFrostWalker(entity)) {
            entity.hurt(entity.damageSources().hotFloor(), 1.0F);
        }
        if (tickCount >= 10) {
            data.payLiquidWalking(entity, TICK_COST);
        }
        data.markLiquidWalking();
        return true;
    }

    // 1.16 HamonUtil.emitHamonSparkParticles(world, clientPlayer, pos, 0.05F) and
    // CustomParticles.createUltimateHamonSparkParticles(null, pos, 3)
    private static void emitSparks(LivingEntity entity) {
        Vec3 pos = entity.position();
        if (entity.level() instanceof ServerLevel level) {
            HamonUtil.emitHamonSparkParticles(level, entity instanceof Player player ? player : null, pos, 0.05F);
            level.sendParticles(UltimateLifeformAddon.RAINBOW_SPARK.get(),
                    pos.x, pos.y, pos.z, 3, 0.05D, 0.05D, 0.05D, 0.25D);
            return;
        }
        // The local player's own events (its isEffectiveAi is true) ran the same calls on the client in 1.16: one
        // Hamon spark and the spark sound for that player, then three rainbow sparks, on top of the server's set.
        Level level = entity.level();
        RandomSource random = level.getRandom();
        addSpark(level, ModParticles.HAMON_SPARK.get(), pos, random);
        level.playSound(entity instanceof Player player ? player : null, pos.x, pos.y, pos.z,
                ModSoundEvents.HAMON_SPARK.get(), SoundSource.AMBIENT,
                Math.min(0.05F * 2.0F, 1.0F), 1.0F + (random.nextFloat() - 0.5F) * 0.15F);
        for (int i = 0; i < 3; i++) {
            addSpark(level, UltimateLifeformAddon.RAINBOW_SPARK.get(), pos, random);
        }
    }

    // 1.16 CustomParticlesHelper.createHamonSparkParticles: gaussian offset 0.05 and speed 0.25
    private static void addSpark(Level level, ParticleOptions particle, Vec3 pos, RandomSource random) {
        level.addParticle(particle,
                pos.x + random.nextGaussian() * 0.05D,
                pos.y + random.nextGaussian() * 0.05D,
                pos.z + random.nextGaussian() * 0.05D,
                random.nextGaussian() * 0.25D,
                random.nextGaussian() * 0.25D,
                random.nextGaussian() * 0.25D);
    }

    private static boolean hasFrostWalker(LivingEntity entity) {
        return EnchantmentHelper.getEnchantmentLevel(entity.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FROST_WALKER), entity) > 0;
    }
}

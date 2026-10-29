package rotp.addons.tickettoride.state;

import rotp.core.init.ModStatusEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;

// 1.16 TearsEffectEventHandler: the misfortunes of a marked entity, each run
// on every update of it.
public final class TearsHazardService {
    private TearsHazardService() {}

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)
                || !TearsWoundService.isMarked(entity)) {
            return;
        }
        RandomSource random = entity.getRandom();
        // onLivingUpdate: slips in the rain and frost in the snow, on the ground
        if (entity.onGround()) {
            if (level.isRaining() && random.nextDouble() < 0.105D
                    && entity.getDeltaMovement().length() > 0.15D) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 300, 2, false, true, true));
                entity.addEffect(new MobEffectInstance(
                        ModStatusEffects.MISSHAPEN_LEGS, 400, 0, false, true, true));
                entity.hurt(entity.damageSources().fall(), 8.0F);
            }
            if (inSnowBiome(level, entity) && random.nextDouble() < 0.075D) {
                entity.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, true, true));
                entity.addEffect(new MobEffectInstance(
                        ModStatusEffects.FREEZE, 200, 2, false, true, true));
            }
        }
        // onPlayerTick: weakness every 100 ticks while it rains
        if (entity instanceof Player && level.isRaining()
                && entity.tickCount % 100 == 0) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, 120, 0, false, true, true));
        }
        primeNearbyTnt(level, entity);
        // onLivingTick: water slows and opens the spirit vision
        if (entity.isInWater() && random.nextDouble() < 0.075D) {
            entity.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true, true));
            entity.addEffect(new MobEffectInstance(
                    ModStatusEffects.SPIRIT_VISION, 200, 0, false, true, true));
        }
        disturbBeehives(level, entity, random);
    }

    // onLivingFall: from 3 blocks, a chance of a bad landing on top of the
    // fall itself.
    public static void onFall(
            LivingEntity entity, float distance) {
        if (!(entity.level() instanceof ServerLevel)
                || !TearsWoundService.isMarked(entity)) {
            return;
        }
        float factor = Math.min(distance / 4.0F, 2.0F);
        RandomSource random = entity.getRandom();
        if (distance < 3.0F
                || random.nextFloat() >= 0.3F * factor) {
            return;
        }
        entity.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                (int) (200.0F * factor),
                2,
                false,
                false,
                false));
        entity.addEffect(new MobEffectInstance(
                ModStatusEffects.MISSHAPEN_LEGS,
                (int) (300.0F * factor),
                0,
                false,
                false,
                false));
        entity.hurt(
                entity.damageSources().fall(),
                5.0F + (distance - 3.0F) * 0.5F);
        entity.playSound(
                SoundEvents.SKELETON_HURT,
                1.0F,
                0.8F + random.nextFloat() * 0.4F);
    }

    private static boolean inSnowBiome(
            ServerLevel level, LivingEntity entity) {
        BlockPos pos = entity.blockPosition();
        // SNOW here already means colder than 0.15 at this position
        return level.getBiome(pos).value().getPrecipitationAt(pos)
                == Biome.Precipitation.SNOW;
    }

    // onTNTApproach: every TNT block within 3 is lit, by the marked entity.
    private static void primeNearbyTnt(
            ServerLevel level, LivingEntity entity) {
        BlockPos center = entity.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-3, -3, -3),
                center.offset(3, 3, 3))) {
            if (!level.getBlockState(pos).is(Blocks.TNT)) {
                continue;
            }
            BlockPos at = pos.immutable();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 11);
            level.addFreshEntity(new PrimedTnt(
                    level,
                    at.getX() + 0.5D,
                    at.getY() + 0.5D,
                    at.getZ() + 0.5D,
                    entity));
            level.playSound(null, at, SoundEvents.TNT_PRIMED,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // onEntityTick: the first beehive within the box grown by 5 may break
    // into three to five angry bees.
    private static void disturbBeehives(
            ServerLevel level, LivingEntity entity, RandomSource random) {
        BlockPos hive = BlockPos.betweenClosedStream(
                        entity.getBoundingBox().inflate(5.0D))
                .filter(pos -> level.getBlockState(pos).is(BlockTags.BEEHIVES))
                .map(BlockPos::immutable)
                .findFirst()
                .orElse(null);
        if (hive == null || random.nextDouble() >= 0.045D) {
            return;
        }
        level.destroyBlock(hive, false);
        for (int i = 0; i < 3 + random.nextInt(3); i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }
            bee.setPos(
                    hive.getX() + 0.5D,
                    hive.getY() + 0.5D,
                    hive.getZ() + 0.5D);
            bee.setTarget(entity);
            bee.setRemainingPersistentAngerTime(400);
            bee.setAge(-1);
            bee.setHealth(10.0F);
            level.addFreshEntity(bee);
        }
        level.playSound(null, hive,
                level.getBlockState(hive).getSoundType(level, hive, null)
                        .getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static void applyBleeding(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(
                ModStatusEffects.BLEEDING, 200, 0, false, true, true));
    }
}

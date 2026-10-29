package rotp.addons.weatherreport;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;

import rotp.addons.weatherreport.block.BloodPuddleBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = WeatherReportAddon.MOD_ID)
public final class WeatherReportBloodEvents {
    public static final double PUDDLE_CHANCE = 0.2D;
    public static final double STAGE_CHANCE = 0.2D;

    private WeatherReportBloodEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || !(living.level() instanceof ServerLevel level)
                || !living.isAlive()
                || isTimeStopped(level, living)) {
            return;
        }

        MobEffectInstance bleeding =
                living.getEffect(ModStatusEffects.BLEEDING);
        if (bleeding == null) {
            return;
        }

        RandomSource random = living.getRandom();
        double spread =
                (bleeding.getAmplifier() + 2.0D)
                        * (random.nextDouble() - 0.5D);
        emitBloodDroplet(level, living, spread, random);
    }

    public static void emitBloodDroplet(
            ServerLevel level,
            LivingEntity living,
            double spread,
            RandomSource random) {
        Vec3 particlePosition = living.position().add(
                (random.nextDouble() - 0.5D)
                        * (living.getBbWidth() + spread),
                random.nextDouble() * living.getBbHeight(),
                (random.nextDouble() - 0.5D)
                        * (living.getBbWidth() + spread));
        level.sendParticles(
                ModParticles.BLOOD.get(),
                particlePosition.x,
                particlePosition.y,
                particlePosition.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);

        if (random.nextDouble() <= 1.0D - PUDDLE_CHANCE) {
            return;
        }

        BlockPos puddlePos = BlockPos.containing(particlePosition);
        if (!level.hasChunkAt(puddlePos)) {
            return;
        }

        BlockState replaced = level.getBlockState(puddlePos);
        BlockState support = level.getBlockState(puddlePos.below());
        if (!(replaced.is(Blocks.AIR)
                        || replaced.is(Blocks.CAVE_AIR))
                || support.getBlock().hasDynamicShape()) {
            return;
        }

        BlockState puddle = WeatherReportAddon.BLOOD_PUDDLE.get()
                .defaultBlockState()
                .setValue(BloodPuddleBlock.STAGE, 0)
                .setValue(
                        BloodPuddleBlock.FACING,
                        BloodPuddleBlock.randomDirection(random));
        if (!puddle.canSurvive(level, puddlePos)) {
            return;
        }

        level.setBlockAndUpdate(puddlePos, puddle);
        inheritNearbyStage(
                level, living, puddlePos, spread, random);
    }

    private static void inheritNearbyStage(
            ServerLevel level,
            LivingEntity living,
            BlockPos puddlePos,
            double radius,
            RandomSource random) {
        if (radius <= 0.0D) {
            return;
        }

        BlockPos min = BlockPos.containing(
                living.getX() - radius,
                living.getY() - radius,
                living.getZ() - radius);
        BlockPos max = BlockPos.containing(
                living.getX() + radius,
                living.getY() + radius,
                living.getZ() + radius);
        for (BlockPos nearby : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(nearby)) {
                continue;
            }
            BlockState state = level.getBlockState(nearby);
            if (state.is(WeatherReportAddon.BLOOD_PUDDLE.get())
                    && state.getValue(BloodPuddleBlock.STAGE) < 3
                    && random.nextDouble() < STAGE_CHANCE) {
                level.setBlockAndUpdate(
                        puddlePos,
                        state.setValue(
                                BloodPuddleBlock.STAGE,
                                state.getValue(
                                        BloodPuddleBlock.STAGE)
                                        + 1));
            }
        }
    }

    private static boolean isTimeStopped(
            ServerLevel level, LivingEntity living) {
        var timeStop = ModDataAttachmentTypes.TIME_STOP.get();
        return level.hasData(timeStop)
                && level.getData(timeStop).shouldFreeze(living);
    }
}

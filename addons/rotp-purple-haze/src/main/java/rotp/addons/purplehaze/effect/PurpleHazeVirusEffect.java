package rotp.addons.purplehaze.effect;

import java.util.Set;

import rotp.addons.purplehaze.PurpleHazeAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

public final class PurpleHazeVirusEffect extends MobEffect {
    public static final int COLOR = 0x883AAA;

    public PurpleHazeVirusEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        int interval = 25 >> Math.max(0, amplifier);
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide() && entity.isAlive()) {
            entity.hurt(
                    entity.damageSources().magic(),
                    Math.max(0, amplifier) + 0.5F);
        }
        return true;
    }

    @Override
    public void fillEffectCures(
            Set<EffectCure> cures,
            MobEffectInstance effectInstance) {
        cures.clear();
    }

    public static boolean applyVirus(
            LivingEntity target,
            int amplifier,
            int duration) {
        if (target.level().isClientSide()
                || !target.isAlive()
                || duration <= 0) {
            return false;
        }

        int effectiveDuration = duration;
        int effectiveAmplifier = Math.max(0, amplifier);
        BlockPos position = target.blockPosition();
        if (target.level().isDay()
                && target.level().canSeeSky(position)) {
            effectiveDuration = Math.max(1, duration / 2);
            effectiveAmplifier =
                    Math.round(effectiveAmplifier / 2.0F);
        }

        return target.addEffect(new MobEffectInstance(
                PurpleHazeAddon.PH_VIRUS,
                effectiveDuration,
                effectiveAmplifier,
                false,
                false,
                true));
    }

    public static boolean applyVirusUnattenuated(
            LivingEntity target,
            int amplifier,
            int duration) {
        return !target.level().isClientSide()
                && target.isAlive()
                && target.addEffect(new MobEffectInstance(
                        PurpleHazeAddon.PH_VIRUS,
                        Math.max(1, duration),
                        Math.max(0, amplifier),
                        false,
                        false,
                        true));
    }
}

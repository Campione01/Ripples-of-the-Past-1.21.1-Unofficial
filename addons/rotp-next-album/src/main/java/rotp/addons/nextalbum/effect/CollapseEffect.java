package rotp.addons.nextalbum.effect;

import java.util.Set;

import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.EffectCure;

public final class CollapseEffect extends MobEffect {
    public CollapseEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        entity.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                4,
                1,
                false,
                false,
                false));
        // 1.16 StaggeredEffectEventHandler: field_76437_t = Weakness.
        entity.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS,
                4,
                1,
                false,
                false,
                false));
        entity.setSwimming(true);
        entity.setPose(Pose.SWIMMING);
        if (entity instanceof Player player) {
            player.setForcedPose(Pose.SWIMMING);
            CollapsePoseState.markLocked(player);
            player.stopFallFlying();
            // 1.16 StaggeredEffectEventHandler: stopUsingItem every tick.
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
        }
        if (!entity.level().isClientSide()) {
            StandPower stand = StandPower.get(entity);
            if (stand != null) {
                stand.interruptHeldInputs();
            }
            if (stand != null
                    && stand.hasPower()
                    && stand.getPowerType() != null) {
                stand.getPowerType().forceUnsummon(entity, stand);
            }
        }
        return true;
    }

    @Override
    public void fillEffectCures(
            Set<EffectCure> cures,
            MobEffectInstance effectInstance) {
        cures.clear();
    }
}

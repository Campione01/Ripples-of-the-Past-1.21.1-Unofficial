package rotp.addons.tusk.effect;

import javax.annotation.Nullable;

import rotp.core.customobjects.StatusEffectModified;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskInfiniteRotationState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class InfiniteRotationEffect
        extends StatusEffectModified {
    public static final int COLOR = 0xFFC10A;
    private static final double ROTATION_FACTOR = 0.25D;

    public InfiniteRotationEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        isUncurable = true;
    }

    @Override
    public void onAdded(
            LivingEntity target,
            MobEffectInstance instance,
            @Nullable Entity source) {
        if (!target.level().isClientSide()) {
            TuskAddon.infiniteRotationState(target)
                    .initialize(target, source);
        }
    }

    @Override
    public void onRemoved(
            LivingEntity target, MobEffectInstance instance) {
        if (!target.level().isClientSide()) {
            TuskAddon.infiniteRotationState(target).clear(target);
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        int interval = 10 >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity target, int amplifier) {
        if (!(target.level() instanceof ServerLevel level)) {
            return true;
        }

        TuskInfiniteRotationState state =
                TuskAddon.infiniteRotationState(target);
        state.initialize(target, null);

        if (target instanceof Player player) {
            if (player.getAbilities().invulnerable) {
                player.getAbilities().invulnerable = false;
                player.onUpdateAbilities();
            }
        } else if (target.isInvulnerable()) {
            target.setInvulnerable(false);
        }

        // Donor 058 hands a living mount new EffectInstance(this): zero
        // duration, so the mount drops it on its next tick before any
        // rotation tick runs and never keeps Infinite Rotation itself.
        if (target.getVehicle() instanceof LivingEntity vehicle
                && !vehicle.hasEffect(TuskAddon.INFINITE_ROTATION)) {
            Entity source = state.resolveSource(level).orElse(null);
            vehicle.addEffect(
                    new MobEffectInstance(
                            TuskAddon.INFINITE_ROTATION),
                    source);
        }

        Vec3 rotationVelocity = target.position()
                .subtract(state.anchor())
                .scale(ROTATION_FACTOR);
        target.setDeltaMovement(rotationVelocity);
        target.hurtMarked = true;
        return true;
    }
}

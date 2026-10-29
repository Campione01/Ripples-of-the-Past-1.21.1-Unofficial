package rotp.addons.whitesnake.blindness;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class BlindnessState
        implements INBTSerializable<CompoundTag> {
    private static final String ACTIVE_TAG = "Active";
    private static final int BLINDNESS_DURATION = 100_000;
    private static final int BLINDNESS_AMPLIFIER = 1;

    private boolean active;

    public boolean isActive() {
        return active;
    }

    public void activate(LivingEntity user) {
        active = true;
        user.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                BLINDNESS_DURATION,
                BLINDNESS_AMPLIFIER,
                false,
                false,
                false));
    }

    public void clear(LivingEntity user, boolean removeEffect) {
        boolean wasActive = active;
        active = false;
        if (removeEffect && wasActive) {
            user.removeEffect(MobEffects.BLINDNESS);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(ACTIVE_TAG, active);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        active = tag.getBoolean(ACTIVE_TAG);
    }
}

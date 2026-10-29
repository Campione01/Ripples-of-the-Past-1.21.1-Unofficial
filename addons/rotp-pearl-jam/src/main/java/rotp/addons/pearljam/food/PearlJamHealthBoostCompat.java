package rotp.addons.pearljam.food;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

public final class PearlJamHealthBoostCompat {
    public static final int WEAK_AMPLIFIER = -2;
    private static final String WEAK_TAG = "rotp_pj:weak_health_boost";
    private static final String HIDDEN_TAG = "hidden_effect";

    private PearlJamHealthBoostCompat() {}

    public static void markWeak(MobEffectInstance instance) {
        if (!(instance instanceof PearlJamWeakHealthBoostAccess access)) {
            throw new IllegalStateException(
                    "Pearl Jam Health Boost mixin is not applied");
        }
        access.rotp_pj$setWeakHealthBoost(true);
    }

    public static boolean isWeak(MobEffectInstance instance) {
        return instance != null
                && instance.is(MobEffects.HEALTH_BOOST)
                && instance instanceof PearlJamWeakHealthBoostAccess access
                && access.rotp_pj$getRawAmplifier() == 0
                && access.rotp_pj$isWeakHealthBoost();
    }

    public static int comparisonAmplifier(MobEffectInstance instance) {
        if (isWeak(instance)) {
            return WEAK_AMPLIFIER;
        }
        return instance instanceof PearlJamWeakHealthBoostAccess access
                ? access.rotp_pj$getRawAmplifier()
                : instance.getAmplifier();
    }

    public static void applyActiveModifiers(LivingEntity consumer) {
        if (consumer.level().isClientSide()) {
            return;
        }
        MobEffectInstance active = consumer.getEffect(MobEffects.HEALTH_BOOST);
        if (!isWeak(active)
                || !active.isInfiniteDuration() && active.getDuration() <= 0) {
            return;
        }
        // Use the native modifier id, so ordinary effect removal owns cleanup.
        active.getEffect().value().createModifiers(WEAK_AMPLIFIER,
                (attribute, modifier) -> {
                    var instance = consumer.getAttributes().getInstance(attribute);
                    if (instance != null
                            && !modifier.equals(instance.getModifier(modifier.id()))) {
                        instance.addOrReplacePermanentModifier(modifier);
                    }
                });
    }

    public static void writeMarkers(MobEffectInstance instance, CompoundTag tag) {
        MobEffectInstance current = instance;
        CompoundTag currentTag = tag;
        while (current != null) {
            if (isWeak(current)) {
                currentTag.putBoolean(WEAK_TAG, true);
            } else {
                currentTag.remove(WEAK_TAG);
            }
            current = current.hiddenEffect;
            if (current == null || !currentTag.contains(HIDDEN_TAG, Tag.TAG_COMPOUND)) {
                return;
            }
            currentTag = currentTag.getCompound(HIDDEN_TAG);
        }
    }

    public static void readMarkers(MobEffectInstance instance, CompoundTag tag) {
        MobEffectInstance current = instance;
        CompoundTag currentTag = tag;
        while (current != null) {
            if (current instanceof PearlJamWeakHealthBoostAccess access) {
                access.rotp_pj$setWeakHealthBoost(
                        current.is(MobEffects.HEALTH_BOOST)
                                && current.getAmplifier() == 0
                                && currentTag.getBoolean(WEAK_TAG));
            }
            current = current.hiddenEffect;
            if (current == null || !currentTag.contains(HIDDEN_TAG, Tag.TAG_COMPOUND)) {
                return;
            }
            currentTag = currentTag.getCompound(HIDDEN_TAG);
        }
    }
}

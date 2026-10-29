package rotp.addons.sexpistols.content.sexpistols;

import rotp.core.init.ModStatusEffects;

import rotp.addons.sexpistols.registry.SexPistolsEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class SexPistolsJoyful {
    public static final int DURATION_TICKS = 4_800;
    public static final int MAX_AMPLIFIER = 4;

    private SexPistolsJoyful() {}

    public static int level(LivingEntity entity) {
        MobEffectInstance effect = entity != null
                ? entity.getEffect(SexPistolsEffects.JOYFUL)
                : null;
        return effect != null ? effect.getAmplifier() + 1 : 0;
    }

    public static void addFromFood(
            LivingEntity entity, int nutrition, float saturation) {
        if (entity == null || entity.level().isClientSide()) {
            return;
        }
        int foodValue = Math.max(1, nutrition)
                + Math.max(0, Math.round(saturation * 2.0F));
        int gained = Math.max(
                0, Math.min(MAX_AMPLIFIER, (foodValue - 1) / 4));
        MobEffectInstance current =
                entity.getEffect(SexPistolsEffects.JOYFUL);
        int amplifier = current != null
                ? Math.max(current.getAmplifier(), gained)
                : gained;
        int duration = current != null
                ? Math.max(current.getDuration(), DURATION_TICKS)
                : DURATION_TICKS;
        entity.addEffect(new MobEffectInstance(
                SexPistolsEffects.JOYFUL,
                duration,
                Math.min(MAX_AMPLIFIER, amplifier),
                false,
                false,
                true));
    }

    public static double healthMultiplier(LivingEntity entity) {
        return 1.0D + level(entity) * 0.035D;
    }

    public static double movementMultiplier(LivingEntity entity) {
        return 1.0D + level(entity) * 0.025D;
    }

    public static float applyBulletDamage(
            LivingEntity entity, float baseDamage) {
        int level = level(entity);
        if (level <= 0
                || entity.getRandom().nextFloat()
                        >= bulletBoostChance(level)) {
            return baseDamage;
        }
        return baseDamage * (1.12F + level * 0.06F);
    }

    public static float bulletBoostChance(int level) {
        return Math.min(0.6F, 0.08F + level * 0.07F);
    }

    public static float criticalChance(LivingEntity entity) {
        float resolve = entity != null
                        && entity.hasEffect(ModStatusEffects.RESOLVE)
                ? 0.1F
                : 0.0F;
        return Math.min(1.0F, level(entity) * 0.1F + resolve);
    }
}

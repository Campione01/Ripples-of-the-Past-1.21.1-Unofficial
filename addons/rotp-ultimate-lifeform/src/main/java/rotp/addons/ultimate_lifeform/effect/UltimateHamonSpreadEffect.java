package rotp.addons.ultimate_lifeform.effect;

import rotp.core.mechanics.JojoDefinitions;
import rotp.core.util.functions.DamageUtil;
import rotp.addons.ultimate_lifeform.UltimateHamonCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

public final class UltimateHamonSpreadEffect extends MobEffect {
    public UltimateHamonSpreadEffect() {
        super(MobEffectCategory.HARMFUL, 0xFFC10A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    /*
     * 1.16 UltimateHamonSpreadEffect.performEffect: plain hurts with
     * DamageUtil.HAMON on every tick, under the target's hurt cooldown (so
     * the hit landed again once the cooldown fell to 10 ticks) and without a
     * knockback; the second hit for undead mobs (CreatureAttribute.UNDEAD).
     */
    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide()) {
            DamageSource source = UltimateHamonCharge.plainHamonSource(
                    entity.level());
            DamageUtil.hurtRespectingCooldown(
                    entity,
                    source,
                    2.0F + amplifier * 0.5F);
            if (entity.getType().is(EntityTypeTags.UNDEAD)) {
                DamageUtil.hurtRespectingCooldown(
                        entity,
                        source,
                        1.0F + amplifier * 0.5F);
            }
        }
        return true;
    }

    /*
     * 1.16 CustomDamageSources.stackUltimateHamonBurn: a burning target goes
     * one level up (to V at most), otherwise the start level; 12000 ticks for
     * an undead or vampiric target, 200 for any other.
     */
    public static void stackUltimateHamonBurn(
            LivingEntity target, int amplifierStart) {
        MobEffectInstance burning =
                target.getEffect(UltimateLifeformAddon.HAMON_SPREAD);
        int amplifier = burning != null
                ? Math.min(4, burning.getAmplifier() + 1)
                : amplifierStart;
        target.addEffect(new MobEffectInstance(
                UltimateLifeformAddon.HAMON_SPREAD,
                JojoDefinitions.isUndeadOrVampiric(target) ? 12000 : 200,
                amplifier,
                false,
                false,
                true));
    }
}

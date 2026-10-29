package rotp.addons.reworked.effect;

import rotp.core.customobjects.StatusEffectModified;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.reworked.ReworkedAddon;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class SlowBurnEffect extends StatusEffectModified {
    public static final int COLOR = 15_621_421;

    public SlowBurnEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                ReworkedAddon.id("slowburn_movement_speed"),
                -1.0D,
                AttributeModifier.Operation.ADD_VALUE);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                ReworkedAddon.id("slowburn_attack_speed"),
                -0.5D,
                AttributeModifier.Operation.ADD_VALUE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return true;
        }

        int transferredAmplifier = amplifier - 6;
        int fireTicks = entity.getRemainingFireTicks();
        if (transferredAmplifier < 0) {
            int period =
                    20 * (Math.abs(transferredAmplifier) + 1);
            if (fireTicks % 20 == 0
                    && fireTicks % period != 0) {
                entity.setRemainingFireTicks(
                        Math.max(0, fireTicks - 2));
            }
        }
        else if (transferredAmplifier != 0) {
            int quotient = 20 / transferredAmplifier;
            int interval = quotient != 0 ? quotient : 1;
            for (int i = 0; i < transferredAmplifier; i++) {
                if ((fireTicks + 10 - interval * (i + 1))
                        % 20 == 0) {
                    DamageUtil.hurtThroughInvulTicks(
                            entity,
                            entity.damageSources().onFire(),
                            1.0F);
                }
            }
        }
        return true;
    }
}

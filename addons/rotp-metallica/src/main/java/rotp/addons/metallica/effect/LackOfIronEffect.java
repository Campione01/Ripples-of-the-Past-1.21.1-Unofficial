package rotp.addons.metallica.effect;

import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.state.MetallicaIronState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.NeoForgeMod;

public final class LackOfIronEffect
        extends StatusEffectModified
        implements StatusEffectApplicable {
    public static final int COLOR = 0xFF9400;
    public static final float SUFFOCATION_RATE = 0.01F;
    public static final float VAMPIRE_BLOOD_DRAIN_PER_TICK = 1.5F;

    public LackOfIronEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
        setUncurable();
        // 1.16: attack damage and attack speed, not armor.
        addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                MetallicaAddon.id("lack_of_iron_attack_damage"),
                -0.15D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.ATTACK_SPEED,
                MetallicaAddon.id(
                        "lack_of_iron_attack_speed"),
                -0.15D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                NeoForgeMod.SWIM_SPEED,
                MetallicaAddon.id("lack_of_iron_swim_speed"),
                -0.30D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                MetallicaAddon.id(
                        "lack_of_iron_movement_speed"),
                -0.25D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(
            LivingEntity target, int amplifier) {
        if (!(target.level() instanceof ServerLevel level)) {
            return true;
        }
        MetallicaIronState state =
                MetallicaAddon.ironState(target);
        LivingEntity source = state.resolveSource(level)
                .orElse(null);
        DamageUtil.suffocateTick(
                target, source, SUFFOCATION_RATE);
        PlayerPower.getPowerData(
                        target,
                        ModPlayerPowers.VAMPIRISM)
                .ifPresent(vampirism ->
                        vampirism.consumeBlood(
                                target,
                                VAMPIRE_BLOOD_DRAIN_PER_TICK));
        return true;
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return true;
    }
}

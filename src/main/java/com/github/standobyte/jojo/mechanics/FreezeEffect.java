package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;
import com.github.standobyte.jojo.init.ModStatusEffects;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.DamageUtil;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class FreezeEffect extends StatusEffectModified implements StatusEffectApplicable {

	public FreezeEffect(MobEffectCategory category, int color) {
		super(category, color);
		addAttributeModifier(Attributes.MOVEMENT_SPEED, JojoMod.resLoc("effect.freeze.movement_speed"),
				-0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.ATTACK_SPEED, JojoMod.resLoc("effect.freeze.attack_speed"),
				-0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (!entity.level().isClientSide()
				&& (entity.getRemainingFireTicks() > 0 || entity.level().dimensionType().ultraWarm())) {
			entity.removeEffect(ModStatusEffects.FREEZE);
		}
		return true;
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return !DamageUtil.isImmuneToCold(entity)
				|| PlayerPower.getPowerData(entity, ModPlayerPowers.VAMPIRISM).isPresent();
	}
}

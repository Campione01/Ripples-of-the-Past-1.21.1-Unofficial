package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.StatusEffectApplicable;
import com.github.standobyte.jojo.customobjects.StatusEffectModified;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForgeMod;

public class ImmobilizeEffect extends StatusEffectModified implements StatusEffectApplicable {

	public ImmobilizeEffect(int color) {
		super(MobEffectCategory.HARMFUL, color);
		setUncurable();
		addAttributeModifier(Attributes.MOVEMENT_SPEED, JojoMod.resLoc("effect.immobilize.movement_speed"),
				-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.FLYING_SPEED, JojoMod.resLoc("effect.immobilize.flying_speed"),
				-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(Attributes.ATTACK_SPEED, JojoMod.resLoc("effect.immobilize.attack_speed"),
				-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		addAttributeModifier(NeoForgeMod.SWIM_SPEED, JojoMod.resLoc("effect.immobilize.swim_speed"),
				-1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity entity, int amplifier) {
		if (entity instanceof Player player) {
			player.getAbilities().flying = false;
		}
		if (resetsDeltaMovement()) {
			entity.setDeltaMovement(0, Math.min(entity.getDeltaMovement().y, 0), 0);
		}
		return true;
	}

	public boolean resetsDeltaMovement() {
		return true;
	}

	@Override
	public boolean isApplicable(LivingEntity entity) {
		return !(entity instanceof Player player && player.getAbilities().instabuild);
	}
}

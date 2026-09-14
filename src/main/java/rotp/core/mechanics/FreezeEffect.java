package rotp.core.mechanics;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.StatusEffectApplicable;
import rotp.core.customobjects.StatusEffectModified;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.util.functions.DamageUtil;

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

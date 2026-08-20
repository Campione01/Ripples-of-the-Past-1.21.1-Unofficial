package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.FluidState;

public final class HamonMovementHelper {
	private HamonMovementHelper() {}

	public static boolean onLiquidWalkingEvent(LivingEntity entity, FluidState fluidState) {
		if (entity == null || fluidState == null || fluidState.isEmpty()) {
			return false;
		}
		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		if (!isLiquidWalking(entity, hamon, fluidState)) {
			return false;
		}
		hamon.setWaterWalkingThisTick();
		return true;
	}

	private static boolean isLiquidWalking(LivingEntity entity, HamonData hamon, FluidState fluidState) {
		if (hamon == null || !hamon.isSkillLearned(ModHamonSkills.LIQUID_WALKING.get())) {
			return false;
		}
		if (entity.isShiftKeyDown() && hamon.getDoubleShiftPress()) {
			return false;
		}
		if (fluidState.is(FluidTags.WATER) && entity.isOnFire()) {
			return false;
		}
		float tickCost = hamon.waterWalkingTickCost();
		if (!hamon.hasEnergy(tickCost, entity)) {
			return false;
		}
		entity.setOnGround(true);

		if (!entity.level().isClientSide()) {
			if (fluidState.is(FluidTags.LAVA) && !entity.fireImmune() && !hasFrostWalker(entity)) {
				entity.hurt(entity.damageSources().hotFloor(), 1.0F);
			}
			hamon.consumeEnergy(tickCost, entity);
		}
		return true;
	}

	private static boolean hasFrostWalker(LivingEntity entity) {
		return EnchantmentHelper.getEnchantmentLevel(entity.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.FROST_WALKER), entity) > 0;
	}
}

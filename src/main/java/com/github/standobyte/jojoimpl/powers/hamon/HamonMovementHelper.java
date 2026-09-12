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

	// Called from collision-shape queries: never mutate movement or spend resources here.
	public static boolean onLiquidWalkingEvent(LivingEntity entity, FluidState fluidState) {
		if (entity == null || fluidState == null || fluidState.isEmpty()) {
			return false;
		}
		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		return isLiquidWalking(entity, hamon, fluidState);
	}

	public static boolean onLiquidWalkingContact(LivingEntity entity, FluidState fluidState) {
		if (entity == null || fluidState == null || fluidState.isEmpty()) {
			return false;
		}
		HamonData hamon = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON).orElse(null);
		if (!isLiquidWalking(entity, hamon, fluidState)) {
			return false;
		}
		if (hamon.claimWaterWalkingContact(entity.tickCount) && !entity.level().isClientSide()) {
			if (fluidState.is(FluidTags.LAVA) && !entity.fireImmune() && !hasFrostWalker(entity)) {
				entity.hurt(entity.damageSources().hotFloor(), 1.0F);
			}
			hamon.consumeEnergy(hamon.waterWalkingTickCost(), entity);
		}
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
		// Supporting-block queries can repeat after the movement's last affordable debit.
		return hamon.hasWaterWalkingContact(entity.tickCount)
				|| hamon.hasEnergy(hamon.waterWalkingTickCost(), entity);
	}

	public record FluidContact(double verticalDisplacement, FluidState fluidState) {}

	private static boolean hasFrostWalker(LivingEntity entity) {
		return EnchantmentHelper.getEnchantmentLevel(entity.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.FROST_WALKER), entity) > 0;
	}
}

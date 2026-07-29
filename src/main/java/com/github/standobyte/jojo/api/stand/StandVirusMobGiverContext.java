package com.github.standobyte.jojo.api.stand;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Immutable context for an addon-owned Stand-virus lifecycle.
 */
public record StandVirusMobGiverContext(
		ResourceLocation owner,
		LivingEntity target,
		ItemStack arrowItem,
		@Nullable Entity arrowShooter,
		int amplifier,
		float baseDamage) {

	public StandVirusMobGiverContext {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(target, "target");
		arrowItem = arrowItem != null ? arrowItem.copy() : ItemStack.EMPTY;
		amplifier = Math.max(0, amplifier);
		baseDamage = Math.max(0.0F, baseDamage);
	}

	@Override
	public ItemStack arrowItem() {
		return arrowItem.copy();
	}
}

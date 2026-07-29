package com.github.standobyte.jojo.api.item;

import net.minecraft.world.item.ItemStack;

/**
 * Declares addon-owned item stacks that leave the user's fingers available
 * for abilities requiring a free hand.
 */
@FunctionalInterface
public interface ItemHandFreePredicate {
	boolean isHandFree(ItemStack stack);
}

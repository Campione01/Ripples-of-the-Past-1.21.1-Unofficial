package com.github.standobyte.jojo.api.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Lets an item replace only the stack used by vanilla eating/drinking
 * particles. Particle count, position, velocity, timing, and dispatch remain
 * owned by vanilla.
 */
public interface ItemUseParticleProvider {
	ItemStack getUseParticleStack(
			LivingEntity user,
			ItemStack usedStack);
}

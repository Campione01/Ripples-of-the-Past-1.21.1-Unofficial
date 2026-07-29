package com.github.standobyte.jojo.api.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface LivingHandUseBlocker {
	boolean isBlocked(
			LivingEntity entity,
			InteractionHand hand);
}

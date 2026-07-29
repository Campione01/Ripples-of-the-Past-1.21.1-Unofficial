package com.github.standobyte.jojo.api.control;

import net.minecraft.world.entity.LivingEntity;

/**
 * Side-neutral context for one vanilla swing-duration calculation.
 */
public record LivingSwingDurationQuery(
		LivingEntity entity,
		int currentDuration) {}

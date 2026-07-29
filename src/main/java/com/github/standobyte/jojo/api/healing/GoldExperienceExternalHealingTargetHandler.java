package com.github.standobyte.jojo.api.healing;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@FunctionalInterface
public interface GoldExperienceExternalHealingTargetHandler {
	/**
	 * Resolves a raw target without mutating game state.
	 */
	@Nullable
	GoldExperienceExternalHealingTarget resolve(
			Entity rawTarget,
			LivingEntity healer);
}

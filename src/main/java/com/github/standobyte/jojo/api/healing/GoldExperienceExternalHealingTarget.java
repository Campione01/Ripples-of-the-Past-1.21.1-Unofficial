package com.github.standobyte.jojo.api.healing;

import java.util.Objects;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * An addon target resolved into the entities used by Gold Experience.
 *
 * @param rawTarget entity selected by the player and retained for display
 * @param classificationOwner living entity used for living classification
 * @param healingTarget living entity used by vanilla checks and application
 */
public record GoldExperienceExternalHealingTarget(
		Entity rawTarget,
		LivingEntity classificationOwner,
		LivingEntity healingTarget) {
	public GoldExperienceExternalHealingTarget {
		Objects.requireNonNull(rawTarget, "rawTarget");
		Objects.requireNonNull(
				classificationOwner, "classificationOwner");
		Objects.requireNonNull(healingTarget, "healingTarget");
	}
}

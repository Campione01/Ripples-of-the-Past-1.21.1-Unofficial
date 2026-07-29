package com.github.standobyte.jojo.api.stand;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Immutable context used to decide whether an otherwise ordinary damage
 * source may affect Stand manifestations.
 */
public record StandDamageQuery(
		LivingEntity attacker,
		DamageSource source) {}

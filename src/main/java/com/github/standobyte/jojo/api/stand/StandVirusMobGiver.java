package com.github.standobyte.jojo.api.stand;

import net.minecraft.world.entity.LivingEntity;

/**
 * Addon-owned Stand-virus behavior for living entities that the core Stand
 * Arrow does not handle itself.
 */
public interface StandVirusMobGiver {
	/**
	 * Must be side-effect free. The first matching registered giver owns the
	 * virus lifecycle for the target.
	 */
	boolean matches(LivingEntity target);

	/**
	 * Chance in the inclusive range {@code [0, 1]} that the target survives
	 * and proceeds to {@link #giveStand(StandVirusMobGiverContext)}.
	 */
	float survivalChance(StandVirusMobGiverContext context);

	/**
	 * Performs the addon-defined grant or conversion after the core chance
	 * roll succeeds.
	 */
	boolean giveStand(StandVirusMobGiverContext context);

	/**
	 * Health at or below which the virus is resolved after the core damage
	 * step. A negative value disables this additional stop condition.
	 */
	default float stopHealth(StandVirusMobGiverContext context) {
		return -1.0F;
	}

	/**
	 * Additional damage applied after the core virus damage and stop checks.
	 */
	default float extraDamage(StandVirusMobGiverContext context) {
		return 0.0F;
	}
}

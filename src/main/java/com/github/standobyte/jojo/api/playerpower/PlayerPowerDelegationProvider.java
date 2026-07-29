package com.github.standobyte.jojo.api.playerpower;

import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;

/**
 * Authorizes a retained PlayerPower type to remain behaviorally active while
 * an addon-owned temporary type is current.
 */
@FunctionalInterface
public interface PlayerPowerDelegationProvider {
	boolean delegates(
			PlayerPower power,
			PlayerPowerType<?> retainedType);
}

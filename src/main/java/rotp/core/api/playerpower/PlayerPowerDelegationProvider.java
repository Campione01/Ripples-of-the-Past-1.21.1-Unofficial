package rotp.core.api.playerpower;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;

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

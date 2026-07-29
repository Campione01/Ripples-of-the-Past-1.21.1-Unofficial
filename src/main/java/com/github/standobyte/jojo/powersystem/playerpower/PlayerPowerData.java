package com.github.standobyte.jojo.powersystem.playerpower;

import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;

public abstract class PlayerPowerData extends PowerData {
	
	public PlayerPowerData(PlayerPowerType<?> powerType) {
		super(powerType);
	}
	
	@Override
	public PlayerPowerType<?> getPowerType() {
		return (PlayerPowerType<?>) super.getPowerType();
	}

	public float getAbilityCooldownRatio(String abilityName, float partialTick) {
		return 0.0F;
	}

	/**
	 * Suspends passive state that cannot safely remain on the entity while a
	 * temporary power is active. This is deliberately separate from
	 * {@link #onPowerCleared}; temporary transitions retain this data and must
	 * not run destructive clear behavior.
	 */
	public void onTemporaryPowerSuspended(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {}

	/**
	 * Removes passive state owned by a temporary power from both the source
	 * entity and its post-death replacement before the retained power is
	 * restored.
	 */
	public void onTemporaryPowerEnded(
			PlayerPower temporaryPower,
			PlayerPower restoredPower,
			PlayerPowerType<?> restoredType) {}

	/**
	 * Reapplies passive state after retained data is installed directly,
	 * without invoking acquisition behavior such as energy grants or sounds.
	 */
	public void onTemporaryPowerRestored(
			PlayerPower power,
			PlayerPowerType<?> temporaryType) {}

	/**
	 * Advances only state that the 1.16.5 shared power tracker continued to
	 * tick while a temporary power was active. Implementations must not run
	 * ordinary energy, passive-effect, or ability behavior here.
	 */
	public void tickWhileTemporarilySuspended(
			PlayerPower power) {}
	
	@Override public PowerClass<?> getPowerClass() { return PowerClass.PLAYER_POWER; }
}

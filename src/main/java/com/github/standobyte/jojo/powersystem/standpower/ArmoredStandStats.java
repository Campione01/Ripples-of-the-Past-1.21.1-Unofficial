package com.github.standobyte.jojo.powersystem.standpower;

/**
 * Slice 5a framework extension — minimum ArmoredStandStats API.
 *
 * <p>Wraps a base {@link StandStats} and adds armor-related fields for
 * stands that legacy registered as {@code ArmoredStandSupplier} / armored stand
 * type (notably Silver Chariot). Used by {@code SilverChariotTakeOffArmorAbility}
 * to query / toggle armor-on state at runtime.</p>
 *
 * <p>Slice 5a delivers the data carrier + getters / immutable toggle.
 * Behavior depth (armor-damage scaling, model swap, on-hit damage absorption)
 * is consumed by Slice 5b SC family follow-up.</p>
 */
public class ArmoredStandStats {

	private final StandStats baseStats;
	private final double armorPower;
	private final boolean hasHelmet;

	public ArmoredStandStats(StandStats baseStats, double armorPower, boolean hasHelmet) {
		this.baseStats = baseStats;
		this.armorPower = armorPower;
		this.hasHelmet = hasHelmet;
	}

	public StandStats baseStats() {
		return baseStats;
	}

	public double armorPower() {
		return armorPower;
	}

	public boolean hasHelmet() {
		return hasHelmet;
	}

	public ArmoredStandStats withoutArmor() {
		return new ArmoredStandStats(baseStats, 0.0, false);
	}
}

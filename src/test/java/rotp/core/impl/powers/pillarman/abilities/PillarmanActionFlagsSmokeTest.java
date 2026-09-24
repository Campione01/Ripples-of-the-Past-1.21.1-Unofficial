package rotp.core.impl.powers.pillarman.abilities;

import java.util.function.BiFunction;

import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 ModPillarmanActions: Small Sandstorm is swingHand() without withUserPunch, so Action.onPerform reset the
 * attack strength; Enhanced Senses, Stone Form, Regeneration, Horn Attack and Absorption ignore the performer stun
 * (Stone Form stuns its own user, and Enhanced Senses can be used in stone form). The world checks are in
 * HamonGlovesPillarmanFlagGameTests.
 */
public final class PillarmanActionFlagsSmokeTest {
	private PillarmanActionFlagsSmokeTest() {}

	public static void run() {
		PillarmanSmallSandstormAbility sandstorm = create("pillarman_small_sandstorm", PillarmanSmallSandstormAbility::new);
		check(sandstorm.resetsAttackStrengthOnPerform(),
				"1.16 Small Sandstorm (swingHand, no withUserPunch) resets the attack strength when it performs");
		check(!sandstorm.ignoresPerformerStun(), "1.16 Small Sandstorm did not ignore the performer stun");

		PillarmanEnhancedSensesAbility senses = create("pillarman_enhanced_senses", PillarmanEnhancedSensesAbility::new);
		check(senses.ignoresPerformerStun(),
				"1.16 Enhanced Senses ignores the performer stun (usable in stone form, and a stun does not end the hold)");
		check(!senses.resetsAttackStrengthOnPerform(), "1.16 Enhanced Senses has no swingHand");

		check(create("pillarman_stone_form", PillarmanStoneFormAbility::new).ignoresPerformerStun(),
				"1.16 Stone Form ignores the performer stun");
		check(create("pillarman_regeneration", PillarmanRegenerationAbility::new).ignoresPerformerStun(),
				"1.16 Regeneration ignores the performer stun");
		check(create("pillarman_horn_attack", PillarmanHornAttackAbility::new).ignoresPerformerStun(),
				"1.16 Horn Attack ignores the performer stun");
		PillarmanHeavyPunchAbility heavyPunch = create("pillarman_heavy_punch", PillarmanHeavyPunchAbility::new);
		check(!heavyPunch.ignoresPerformerStun() && !heavyPunch.resetsAttackStrengthOnPerform(),
				"1.16 Pillar Man Heavy Punch neither ignores the stun nor swings its hand");
	}

	private static <T extends PillarmanActionAbility> T create(String name,
			BiFunction<AbilityType<T>, AbilityId, T> factory) {
		return new AbilityType<T>(ResourceLocation.fromNamespaceAndPath("jojo_ripples", "flags_" + name), factory)
				.createInstance(new AbilityId(null, ResourceLocation.fromNamespaceAndPath("jojo_ripples", "test_power"), name));
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

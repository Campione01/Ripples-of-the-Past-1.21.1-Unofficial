package com.github.standobyte.jojoimpl.powers.pillarman.abilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PillarmanAbilityDataOwnershipSmokeTest {
	private static final String SOURCE_ROOT =
			"src/main/java/com/github/standobyte/jojoimpl/powers/pillarman/abilities/";

	private PillarmanAbilityDataOwnershipSmokeTest() {}

	public static void run() {
		assertOwnerLookup("PillarmanErraticBlazeKingAbility", "erraticAbility");
		assertOwnerLookup("PillarmanGiantCarthwheelPrisonAbility", "prisonAbility");
		assertOwnerLookup("PillarmanHornAttackAbility", "hornAbility");
		assertOwnerLookup("PillarmanLightFlashAbility", "flashAbility");
		assertOwnerLookup("PillarmanLightFlashDecoyAbility", "decoyAbility");
		assertOwnerLookup("PillarmanRegenerationAbility", "regenAbility");
		assertOwnerLookup("PillarmanRibsBladesAbility", "ribsAbility");
		assertOwnerLookup("PillarmanSelfDetonationAbility", "detonationAbility");
		assertOwnerLookup("PillarmanSmallSandstormAbility", "sandstormAbility");
	}

	private static void assertOwnerLookup(String abilityClass, String ownerVariable) {
		String source = readSource(abilityClass);
		String compact = source.replaceAll("\\s+", "");
		String ownerLookup = "context.getDataForAbility(" + ownerVariable + ")";

		check(compact.contains(
				"abilityinstanceof" + abilityClass + ownerVariable),
				abilityClass + " instance must type-check its owning ability");
		check(!compact.contains("context.getDataForAbility(this)"),
				abilityClass + " instance must not use itself as an Ability key");
		check(countOccurrences(compact, ownerLookup) == 2,
				abilityClass + " must guard and sync through its owning Ability");
		check(compact.contains(ownerLookup + ".syncOnUpdate(user)"),
				abilityClass + " must retain the power-data restoration sync");
	}

	private static String readSource(String abilityClass) {
		Path path = Path.of(System.getProperty("user.dir"))
				.resolve(SOURCE_ROOT + abilityClass + ".java");
		try {
			return Files.readString(path);
		}
		catch (IOException e) {
			throw new AssertionError("failed to read " + path, e);
		}
	}

	private static int countOccurrences(String text, String needle) {
		int count = 0;
		for (int index = 0;
				(index = text.indexOf(needle, index)) >= 0;
				index += needle.length()) {
			count++;
		}
		return count;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

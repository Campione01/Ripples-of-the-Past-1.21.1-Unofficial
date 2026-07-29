package com.github.standobyte.jojo.powersystem.ability.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.HeldInputEntry;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public final class HeldInputControlSmokeTest {
	private HeldInputControlSmokeTest() {}

	public static void run() {
		Int2ObjectMap<HeldInputEntry> held =
				new Int2ObjectArrayMap<>();
		AtomicInteger releases = new AtomicInteger();
		AtomicInteger syncs = new AtomicInteger();

		held.put(1, new HeldInputEntry(
				(short) 1,
				null,
				user -> releases.incrementAndGet()));
		held.put(2, new HeldInputEntry(
				(short) 2,
				null,
				user -> {
					releases.incrementAndGet();
					throw new IllegalStateException(
							"isolated release failure");
				}));

		check(AbilityInput.hasHeldInput(held, null),
				"held input was not detected");

		check(AbilityInput.interruptHeldInputs(
				held,
				null,
				null,
				key -> syncs.incrementAndGet()),
				"interruption should report a state change");
		check(releases.get() == 2 && syncs.get() == 2,
				"all releases must be isolated and synchronized");
		check(!AbilityInput.hasHeldInput(held, null),
				"held inputs remained after interruption");

		check(!AbilityInput.interruptHeldInputs(
				held,
				null,
				null,
				key -> syncs.incrementAndGet()),
				"repeated interruption must be idempotent");
		check(syncs.get() == 2,
				"idempotent interruption emitted extra sync");
		check(held.isEmpty(),
				"held-input map was not fully drained");

		verifyPublicServerContract();
	}

	private static void verifyPublicServerContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String power = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/Power.java"));
		check(power.contains(
				"public final boolean hasHeldInput()")
				&& power.contains(
						"public final boolean interruptHeldInputs()"),
				"PlayerPower and StandPower must inherit public input control");

		String input = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/ability/input/AbilityInput.java"));
		check(input.contains("user.level().isClientSide()")
				&& input.contains("user.getExistingData("),
				"held-input control must be server-only and non-attaching");
		check(input.contains(
				"ability.abilityId.powerClass(),")
				&& input.contains(
						"entry.powerClass == powerClass"),
				"held inputs must retain and filter their power class");
		check(input.contains(
				"sendToPlayersTrackingEntityAndSelf(")
				&& input.contains(
						"TrAbilityUsePacket.releaseHold("),
				"held-input interruption must synchronize release");

		String standClick = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/entity_useitem/ClStandClickPacket.java"));
		check(standClick.contains(
				"PowerClass.STAND,"),
				"direct Stand item-use holds lost their class tag");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

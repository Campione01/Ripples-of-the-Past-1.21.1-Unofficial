package com.github.standobyte.jojo.powersystem.ability.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput.ReleaseResult;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.InputGenerationTracker;
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
				1L,
				null,
				user -> releases.incrementAndGet()));
		held.put(2, new HeldInputEntry(
				(short) 2,
				2L,
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

		verifyStandItemUseGenerationLifecycle();
		verifyPublicServerContract();
	}

	private static void verifyStandItemUseGenerationLifecycle() {
		short keyId = 4;
		InputGenerationTracker client = new InputGenerationTracker();
		InputGenerationTracker server = new InputGenerationTracker();
		AtomicInteger releases = new AtomicInteger();
		AtomicInteger syncs = new AtomicInteger();

		long pressGeneration = client.nextInputGeneration(keyId);
		check(server.acceptNetworkPressGeneration(
					keyId, pressGeneration),
				"the server rejected a fresh direct Stand item-use press");
		check(!server.acceptNetworkPressGeneration(
					keyId, pressGeneration),
				"the server accepted a duplicate direct Stand item-use press");

		HeldInputEntry serverHeld = new HeldInputEntry(
				keyId, pressGeneration, null,
				user -> releases.incrementAndGet());
		check(serverHeld.generation == pressGeneration,
				"the server did not record the exact Stand item-use generation");
		long releaseGeneration = client.latestInputGeneration(keyId);
		check(AbilityInput.classifyRelease(
					serverHeld,
					server.latestInputGeneration(keyId),
					releaseGeneration) == ReleaseResult.RELEASED,
				"direct Stand item-use release lost its press generation");
		AbilityInput.releaseHeldInput(
				serverHeld, null, syncs::incrementAndGet);
		check(releases.get() == 1 && syncs.get() == 1,
				"direct Stand item-use release did not execute exactly once");

		long nextGeneration = client.nextInputGeneration(keyId);
		check(nextGeneration > releaseGeneration
				&& server.acceptNetworkPressGeneration(
						keyId, nextGeneration),
				"the next same-key input collided with the Stand item-use generation");
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
		int generationAcceptance = standClick.indexOf(
				"inputHandler.acceptNetworkPressGeneration(");
		int itemClick = standClick.indexOf(
				"ServerSideLivingClick.rightClick(");
		int heldInput = standClick.indexOf(
				"HeldInputEntry heldInput = new HeldInputEntry(", itemClick);
		check(standClick.contains("long inputGeneration,")
				&& standClick.contains("PowerClass.STAND,")
				&& standClick.contains("requireOutboundGeneration(")
				&& standClick.contains(
						"buf.readVarLong(), \"Stand item input\"")
				&& generationAcceptance >= 0
				&& itemClick > generationAcceptance
				&& heldInput > itemClick
				&& standClick.indexOf(
						"packet.inputGeneration,", heldInput) > heldInput
				&& !standClick.contains(
						"inputHandler.nextInputGeneration(internalKeyId)"),
				"direct Stand item-use must validate and retain the client generation");

		String standClient = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "client/input/StandVanillaClickInput.java"));
		int clientGeneration = standClient.indexOf(
				"AbilityInput.nextInputGeneration(");
		int heldTimer = standClient.indexOf(
				"putHeldKeyTimer(", clientGeneration);
		int standPacket = standClient.indexOf(
				"new ClStandClickPacket(", clientGeneration);
		check(clientGeneration >= 0
				&& heldTimer > clientGeneration
				&& standPacket > heldTimer
				&& standClient.indexOf(
						"inputGeneration,", standPacket) > standPacket,
				"direct Stand item-use packet lost its client generation");

		String inputHandler = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "client/input/InputHandler.java"));
		int releaseGeneration = inputHandler.indexOf(
				"AbilityInput.keyReleaseAndGetGeneration(");
		int releasePacket = inputHandler.indexOf(
				"ClAbilityInputPacket.releaseHold(", releaseGeneration);
		check(releaseGeneration >= 0
				&& releasePacket > releaseGeneration
				&& inputHandler.indexOf(
						"keyId, inputGeneration", releasePacket) > releasePacket,
				"RMB release must send the exact held input generation");

		String vanillaItemUse = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/entity_useitem/VanillaItemUseAsAction.java"));
		int stopHold = vanillaItemUse.indexOf("void onButtonStopHold()");
		int releaseItem = vanillaItemUse.indexOf(
				"ServerSideLivingClick.releaseUsingItem(", stopHold);
		check(stopHold >= 0 && releaseItem > stopHold,
				"Stand bow/trident release lost its item-use callback");
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

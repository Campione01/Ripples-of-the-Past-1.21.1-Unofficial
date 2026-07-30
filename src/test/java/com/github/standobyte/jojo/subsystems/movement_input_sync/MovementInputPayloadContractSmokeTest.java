package com.github.standobyte.jojo.subsystems.movement_input_sync;

public final class MovementInputPayloadContractSmokeTest {
	private MovementInputPayloadContractSmokeTest() {}

	public static void run() {
		ClPlayerMovementInputPacket legal =
				new ClPlayerMovementInputPacket(42, -1.0F, 1.0F, true, false, true);
		check(PlayerMovementInputData.acceptsServerboundInput(legal, 42),
				"legal movement input for the sender must be accepted");

		ClPlayerMovementInputPacket forged =
				new ClPlayerMovementInputPacket(43, 0.0F, 0.0F, false, false, false);
		check(!PlayerMovementInputData.acceptsServerboundInput(forged, 42),
				"movement input must not mutate another entity");

		ClPlayerMovementInputPacket nonFinite =
				new ClPlayerMovementInputPacket(42, Float.NaN, 0.0F, false, false, false);
		check(!PlayerMovementInputData.acceptsServerboundInput(nonFinite, 42),
				"non-finite movement input must be rejected");

		ClPlayerMovementInputPacket outOfRange =
				new ClPlayerMovementInputPacket(42, 1.01F, 0.0F, false, false, false);
		check(!PlayerMovementInputData.acceptsServerboundInput(outOfRange, 42),
				"movement axis outside the vanilla input range must be rejected");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

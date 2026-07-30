package com.github.standobyte.jojo.network.s2c;

import java.util.Collections;
import java.util.List;

import io.netty.handler.codec.DecoderException;

public final class ClientboundPayloadContractSmokeTest {
	private ClientboundPayloadContractSmokeTest() {}

	public static void run() {
		check(RPSGameStatePacket.validatePickCount(5, 5) == 5,
				"maximum possible first-to-three RPS history must be accepted");
		check(RPSGameStatePacket.validateOutboundPickCount(5) == 5,
				"maximum possible RPS history must remain encodable");
		expectThrows(DecoderException.class,
				() -> RPSGameStatePacket.validatePickCount(6, 6),
				"impossible RPS history above five resolved rounds must be rejected");
		expectThrows(DecoderException.class,
				() -> RPSGameStatePacket.validatePickCount(5, 4),
				"truncated RPS pick history must be rejected");
		expectThrows(IllegalArgumentException.class,
				() -> RPSGameStatePacket.validateOutboundPickCount(6),
				"impossible RPS history must be rejected before encoding");

		TrGELifeformStatePacket normalLifeforms = new TrGELifeformStatePacket(
				1, "minecraft:frog",
				List.of("minecraft:frog"), List.of(), List.of("minecraft:frog"));
		check(normalLifeforms.metLifeformIds().equals(List.of("minecraft:frog")),
				"normal Gold Experience lifeform state must remain valid");
		expectThrows(IllegalArgumentException.class,
				() -> new TrGELifeformStatePacket(
						1, "", Collections.nCopies(4097, "minecraft:frog"),
						List.of(), List.of()),
				"oversized Gold Experience lifeform collection must be rejected before encoding");
		expectThrows(IllegalArgumentException.class,
				() -> new TrGELifeformStatePacket(
						1, "x".repeat(257), List.of(), List.of(), List.of()),
				"oversized Gold Experience lifeform id must be rejected before encoding");
	}

	private static void expectThrows(
			Class<? extends Throwable> expected, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

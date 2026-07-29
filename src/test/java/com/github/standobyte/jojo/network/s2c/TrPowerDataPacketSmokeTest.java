package com.github.standobyte.jojo.network.s2c;

import net.minecraft.resources.ResourceLocation;

public final class TrPowerDataPacketSmokeTest {
	private TrPowerDataPacketSmokeTest() {}

	public static void run() {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "retained_power");
		ResourceLocation otherId = ResourceLocation.fromNamespaceAndPath(
				"rotp_test", "other");

		check(TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, id, true),
				"matching retained Stand data must be accepted");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, id, false),
				"power-class mismatch must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, otherId, true),
				"power-type ID mismatch must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				id, null, true),
				"unknown power-type ID must be rejected");
		check(!TrPowerDataPacket.Handler.isCompatiblePowerType(
				null, id, true),
				"missing packet power-type ID must be rejected");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}

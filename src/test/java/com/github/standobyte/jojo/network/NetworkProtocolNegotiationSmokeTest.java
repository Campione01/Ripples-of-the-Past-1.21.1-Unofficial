package com.github.standobyte.jojo.network;

import java.util.Optional;

import com.github.standobyte.jojo.PacketsRegister;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.negotiation.NegotiableNetworkComponent;
import net.neoforged.neoforge.network.negotiation.NetworkComponentNegotiator;

public final class NetworkProtocolNegotiationSmokeTest {
	private static final ResourceLocation ACTION_PAYLOAD =
			ResourceLocation.fromNamespaceAndPath("jojo_ripples", "action");

	private NetworkProtocolNegotiationSmokeTest() {}

	public static void run() {
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 5,
				"synchronized target-lock settings require core protocol v5 or later");

		var matching = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				"client");
		check(matching.isEmpty(),
				"matching current peers must negotiate the required play payload");

		var oldSettings = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("4"),
				"client");
		check(oldSettings.isPresent() && !oldSettings.get().success(),
				"a v4 peer must fail before interpreting the new settings payload");

		var generationOnly = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("3"),
				"client");
		check(generationOnly.isPresent() && !generationOnly.get().success(),
				"a v3 peer must fail UUID-payload negotiation before play");

		var legacy = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("2"),
				"client");
		check(legacy.isPresent() && !legacy.get().success(),
				"a v2 peer must fail required-payload negotiation before play");
	}

	private static NegotiableNetworkComponent requiredComponent(
			String version) {
		return new NegotiableNetworkComponent(
				ACTION_PAYLOAD,
				version,
				Optional.of(PacketFlow.CLIENTBOUND),
				false);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

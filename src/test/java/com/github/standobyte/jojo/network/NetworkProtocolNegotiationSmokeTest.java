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
		check("4".equals(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				"UUID-bound generation payloads require core protocol v4");

		var matching = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("4"),
				"client");
		check(matching.isEmpty(),
				"matching v4 peers must negotiate the required play payload");

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

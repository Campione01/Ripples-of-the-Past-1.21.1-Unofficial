package rotp.core.network;

import java.util.Arrays;
import java.util.Optional;

import rotp.core.PacketsRegister;
import rotp.core.mechanics.resolve.ResolveBoostsPacket;
import rotp.core.network.s2c.KnockbackResTickPacket;
import rotp.core.network.s2c.TrAfkMenacingParticlePacket;

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
		// a v5 peer would read resolveboost one float short, or miss special_action entries, instead of failing negotiation
		check(Arrays.stream(ResolveBoostsPacket.class.getRecordComponents())
				.anyMatch(component -> component.getName().equals("maxAchievedValue")),
				"resolveboost lost maxAchievedValue; recheck the protocol version");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 6,
				"resolveboost's maxAchievedValue and the synced stand_entity_block need core protocol v6 or later");
		// a v6 peer has no knockbackrestick payload to decode the server's blocked-hit packets with
		check(Arrays.stream(KnockbackResTickPacket.class.getRecordComponents())
				.anyMatch(component -> component.getName().equals("entityId")),
				"knockbackrestick lost its entity id; recheck the protocol version");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 7,
				"the clientbound knockbackrestick payload needs core protocol v7 or later");
		// R148 added clglidercolor, standassign, trspecialeffect, trhamonparticles and trafkmenacing
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 8,
				"R148's added payloads require protocol v8 or later");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 9,
				"Beetle Arrow durability adds a synced config field and requires protocol v9 or later");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 10,
				"owner progression snapshots require core protocol v10 or later");
		// a v10 peer would read trafkmenacing without its owner field and leave the rest of the payload unread
		check(Arrays.stream(TrAfkMenacingParticlePacket.class.getRecordComponents())
				.anyMatch(component -> component.getName().equals("owner")),
				"trafkmenacing lost its owner; recheck the protocol version");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 11,
				"the owner-tagged trafkmenacing payload requires core protocol v11 or later");

		var matching = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				"client");
		check(matching.isEmpty(),
				"matching current peers must negotiate the required play payload");

		var noAfkOwner = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("10"),
				"client");
		check(noAfkOwner.isPresent() && !noAfkOwner.get().success(),
				"a v10 peer must fail before reading the longer trafkmenacing payload");

		var noSkippedProgression = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("9"),
				"client");
		check(noSkippedProgression.isPresent() && !noSkippedProgression.get().success(),
				"a v9 peer must fail before play without the owner progression snapshot");

		var noBeetleDurability = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("8"),
				"client");
		check(noBeetleDurability.isPresent() && !noBeetleDurability.get().success(),
				"a v8 peer must fail before reading the longer synced config payload");

		var noKnockbackResTick = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("6"),
				"client");
		check(noKnockbackResTick.isPresent() && !noKnockbackResTick.get().success(),
				"a v6 peer must fail negotiation before it receives knockbackrestick");

		var noMaxResolve = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("5"),
				"client");
		check(noMaxResolve.isPresent() && !noMaxResolve.get().success(),
				"a v5 peer must fail negotiation before it reads the longer resolveboost payload");

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

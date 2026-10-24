package rotp.core.network;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import rotp.core.PacketsRegister;
import rotp.core.mechanics.resolve.ResolveBoostsPacket;
import rotp.core.network.s2c.KnockbackResTickPacket;
import rotp.core.network.s2c.TrAfkMenacingParticlePacket;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionInstancePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionPhaseTimePacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionWithOBBSyncPacket;
import rotp.core.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
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
		// a v11 peer would stop reading a tracked Hamon user's data before the remembered aura technique
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 12,
				"the Hamon tracking sync's remembered aura technique requires core protocol v12 or later");
		// a v12 peer has no actiontarget payload: its Stand would keep turning to a target the server dropped
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 13,
				"the clientbound actiontarget payload needs core protocol v13 or later");
		// a v13 peer would read the Stand data's highest Resolve level as the next field
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 14,
				"the Stand data's highest Resolve level needs core protocol v14 or later");
		// a v14 peer has no standawakestage payload: it would read a tracked user's Resolve the user's stage turns off
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 15,
				"the clientbound standawakestage payload needs core protocol v15 or later");
		// a v15 peer would read the action slot as the action data's first byte
		for (Class<?> packet : new Class<?>[] { TrEntityActionPhaseTimePacket.class, TrEntityActionTargetPacket.class,
				TrActionSynchedDataPacket.class, TrEntityActionWithOBBSyncPacket.class }) {
			check(Arrays.stream(packet.getRecordComponents()).anyMatch(component -> component.getName().equals("slot")),
					packet.getSimpleName() + " lost its action slot; recheck the protocol version");
		}
		check(Arrays.stream(TrEntityActionInstancePacket.class.getDeclaredFields())
				.anyMatch(field -> field.getName().equals("slot") && field.getType() == int.class),
				"the entity action packet lost its action slot; recheck the protocol version");
		check(obbSlotRoundTrips(LivingComponentAction.STAND_POWER_SLOT),
				"the OBB sync must carry its action's slot: a Stand-power action's OBB reached the client as slot 0");
		check(Integer.parseInt(PacketsRegister.NETWORK_PROTOCOL_VERSION) >= 16,
				"the action sync packets' action slot needs core protocol v16 or later");

		var matching = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				"client");
		check(matching.isEmpty(),
				"matching current peers must negotiate the required play payload");

		var noActionSlot = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("15"),
				"client");
		check(noActionSlot.isPresent() && !noActionSlot.get().success(),
				"a v15 peer must fail before it reads the action sync packets' slot");

		var noAwakeningStage = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("14"),
				"client");
		check(noAwakeningStage.isPresent() && !noAwakeningStage.get().success(),
				"a v14 peer must fail before it receives standawakestage");

		var noActionTarget = NetworkComponentNegotiator.validateComponent(
				requiredComponent(PacketsRegister.NETWORK_PROTOCOL_VERSION),
				requiredComponent("12"),
				"client");
		check(noActionTarget.isPresent() && !noActionTarget.get().success(),
				"a v12 peer must fail before it receives actiontarget");

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

	private static boolean obbSlotRoundTrips(int slot) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
		try {
			TrEntityActionWithOBBSyncPacket.Handler.STREAM_CODEC.encode(buf,
					new TrEntityActionWithOBBSyncPacket(12, new UUID(3L, 4L), 56L, 7, slot));
			TrEntityActionWithOBBSyncPacket read = TrEntityActionWithOBBSyncPacket.Handler.STREAM_CODEC.decode(buf);
			return !buf.isReadable() && read.slot() == slot && read.actionId() == 7 && read.actionGeneration() == 56L;
		}
		finally {
			buf.release();
		}
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

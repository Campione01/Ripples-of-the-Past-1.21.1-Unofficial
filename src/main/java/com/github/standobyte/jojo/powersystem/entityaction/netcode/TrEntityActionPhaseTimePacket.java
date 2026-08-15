package com.github.standobyte.jojo.powersystem.entityaction.netcode;

import java.util.Objects;
import java.util.UUID;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import it.unimi.dsi.fastutil.objects.Object2FloatArrayMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrEntityActionPhaseTimePacket(int performerId, 
		UUID performerUuid,
		long actionGeneration,
		int actionId, 
		Object2FloatMap<ActionPhase> phasesLength, 
		ActionPhase phase, 
		int curPhaseTick) implements CustomPacketPayload {

	public TrEntityActionPhaseTimePacket {
		Objects.requireNonNull(performerUuid, "performerUuid");
		NetworkPayloadValidation.requireOutboundGeneration(
				actionGeneration, "entity action phase");
	}

	private TrEntityActionPhaseTimePacket(RegistryFriendlyByteBuf buf) {
		this(
				buf.readInt(),
				buf.readUUID(),
				buf.readVarLong(),
				buf.readVarInt(),
				PHASE_LENGTHS_CODEC.decode(buf),
				PHASE_CODEC.decode(buf),
				buf.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(performerId);
		buf.writeUUID(performerUuid);
		buf.writeVarLong(actionGeneration);
		buf.writeVarInt(actionId);
		PHASE_LENGTHS_CODEC.encode(buf, phasesLength);
		PHASE_CODEC.encode(buf, phase);
		buf.writeVarInt(curPhaseTick);
	}

	private static final StreamCodec<RegistryFriendlyByteBuf,
			Object2FloatMap<ActionPhase>> PHASE_LENGTHS_CODEC =
			ByteBufCodecs.map(
					size -> new Object2FloatArrayMap<>(),
					NeoForgeStreamCodecs.enumCodec(ActionPhase.class),
					ByteBufCodecs.FLOAT,
					ActionPhase.values().length);
	private static final StreamCodec<FriendlyByteBuf, ActionPhase>
			PHASE_CODEC = NeoForgeStreamCodecs.enumCodec(ActionPhase.class)
					.apply(NetworkUtil::nullableCodec);
	
	private static CustomPacketPayload.Type<TrEntityActionPhaseTimePacket> type;
	
	public static class Handler implements PacketsRegister.PacketCodecHandler<TrEntityActionPhaseTimePacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrEntityActionPhaseTimePacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrEntityActionPhaseTimePacket> reader() {
			return STREAM_CODEC;
		}
		
		
		public static final StreamCodec<RegistryFriendlyByteBuf,
				TrEntityActionPhaseTimePacket> STREAM_CODEC =
				StreamCodec.ofMember(
						TrEntityActionPhaseTimePacket::write,
						TrEntityActionPhaseTimePacket::new);

		@Override
		public void handle(TrEntityActionPhaseTimePacket payload, IPayloadContext context) {
			ClientEntityActionSyncQueue.applyOrQueuePhaseTime(
					context.listener(), payload);
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

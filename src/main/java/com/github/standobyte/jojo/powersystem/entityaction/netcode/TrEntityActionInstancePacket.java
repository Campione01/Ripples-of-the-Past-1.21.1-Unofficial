package com.github.standobyte.jojo.powersystem.entityaction.netcode;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TrEntityActionInstancePacket implements CustomPacketPayload {
	private final int performerId;
	private final UUID performerUuid;
	private final long generation;
	@Nullable private final EntityActionInstance sendAction;
	@Nullable private final byte[] receiveActionData;
	
	public TrEntityActionInstancePacket(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable EntityActionInstance action) {
		this(performerId, performerUuid, generation, action, null);
	}

	private TrEntityActionInstancePacket(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable EntityActionInstance sendAction,
			@Nullable byte[] receiveActionData) {
		this.performerId = performerId;
		this.performerUuid = Objects.requireNonNull(
				performerUuid, "performerUuid");
		this.generation = generation;
		this.sendAction = sendAction;
		this.receiveActionData = receiveActionData;
	}
	
	
	
	private static CustomPacketPayload.Type<TrEntityActionInstancePacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<TrEntityActionInstancePacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrEntityActionInstancePacket> type() {
			return type;
		}

		@Override
		public void encode(TrEntityActionInstancePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.performerId);
			buf.writeUUID(packet.performerUuid);
			buf.writeVarLong(NetworkPayloadValidation.requireOutboundGeneration(
					packet.generation, "entity action"));
			FriendlyByteBuf actionData = new FriendlyByteBuf(
					Unpooled.buffer(
							256,
							NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES));
			try {
				EntityActionInstance.encode(actionData, packet.sendAction);
				int length = NetworkPayloadValidation.requireOutboundByteLength(
						actionData.readableBytes(),
						NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
						"entity action");
				buf.writeBytes(actionData, actionData.readerIndex(), length);
			}
			finally {
				actionData.release();
			}
		}

		@Override
		public TrEntityActionInstancePacket decode(RegistryFriendlyByteBuf buf) {
			int performerId = buf.readInt();
			UUID performerUuid = buf.readUUID();
			long generation = NetworkPayloadValidation.requireGeneration(
					buf.readVarLong(), "entity action");
			return new TrEntityActionInstancePacket(
					performerId, performerUuid, generation, null,
					NetworkUtil.extraPacketDataBytes(
							buf,
							NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
							"entity action"));
		}
		
		@Override
		public void handle(TrEntityActionInstancePacket payload, IPayloadContext context) {
			ClientEntityActionSyncQueue.applyOrQueueAction(
					context.listener(),
					payload.performerId,
					payload.performerUuid,
					payload.generation,
					payload.receiveActionData);
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

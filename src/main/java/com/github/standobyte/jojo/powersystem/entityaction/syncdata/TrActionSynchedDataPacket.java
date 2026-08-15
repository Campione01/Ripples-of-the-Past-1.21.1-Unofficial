package com.github.standobyte.jojo.powersystem.entityaction.syncdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.ClientEntityActionSyncQueue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrActionSynchedDataPacket(
		int entityId,
		UUID performerUuid,
		long actionGeneration,
		List<SynchedEntityData.DataValue<?>> packedItems)
		implements CustomPacketPayload {
	public TrActionSynchedDataPacket {
		Objects.requireNonNull(performerUuid, "performerUuid");
		NetworkPayloadValidation.requireOutboundGeneration(
				actionGeneration, "entity action data");
	}
	private static CustomPacketPayload.Type<TrActionSynchedDataPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrActionSynchedDataPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrActionSynchedDataPacket> type() {
			return type;
		}

		@Override
		public void encode(TrActionSynchedDataPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeUUID(packet.performerUuid);
			buf.writeVarLong(packet.actionGeneration);
			NetworkPayloadValidation.requireOutboundCollectionSize(
					packet.packedItems.size(),
					NetworkPayloadValidation.MAX_ACTION_SYNC_VALUES,
					"entity action data values");

			for (SynchedEntityData.DataValue<?> datavalue : packet.packedItems) {
				datavalue.write(buf);
			}
			buf.writeByte(255);
		}

		@Override
		public TrActionSynchedDataPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			UUID performerUuid = buf.readUUID();
			long actionGeneration = NetworkPayloadValidation.requireGeneration(
					buf.readVarLong(), "entity action data");

			List<SynchedEntityData.DataValue<?>> packedItems = new ArrayList<>();
			int id;
			while ((id = buf.readUnsignedByte()) != 255) {
				NetworkPayloadValidation.requireCollectionSize(
						packedItems.size() + 1,
						NetworkPayloadValidation.MAX_ACTION_SYNC_VALUES,
						"entity action data values");
				packedItems.add(SynchedEntityData.DataValue.read(buf, id));
			}

			return new TrActionSynchedDataPacket(
					entityId, performerUuid, actionGeneration, packedItems);
		}

		@Override
		public void handle(TrActionSynchedDataPacket payload, IPayloadContext context) {
			ClientEntityActionSyncQueue.applyOrQueueSynchedData(
					context.listener(), payload);
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

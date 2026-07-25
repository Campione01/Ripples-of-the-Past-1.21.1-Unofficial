package com.github.standobyte.jojo.network.s2c;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrDirectEntityDataPacket(int entityId, List<SynchedEntityData.DataValue<?>> packedItems) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrDirectEntityDataPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrDirectEntityDataPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrDirectEntityDataPacket> type() {
			return type;
		}

		@Override
		public void encode(TrDirectEntityDataPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			for (SynchedEntityData.DataValue<?> datavalue : packet.packedItems) {
				datavalue.write(buf);
			}
			buf.writeByte(255);
		}

		@Override
		public TrDirectEntityDataPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			List<SynchedEntityData.DataValue<?>> packedItems = new ArrayList<>();
			int id;
			while ((id = buf.readUnsignedByte()) != 255) {
				packedItems.add(SynchedEntityData.DataValue.read(buf, id));
			}
			return new TrDirectEntityDataPacket(entityId, packedItems);
		}

		@Override
		public void handle(TrDirectEntityDataPacket payload, IPayloadContext context) {
			if (payload.packedItems == null || payload.packedItems.isEmpty()) {
				return;
			}
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				entity.getEntityData().assignValues(payload.packedItems);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package com.github.standobyte.jojo.network.s2c;

import java.util.UUID;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.polaroid.PhotosCache;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PhotoIdAssignedPacket(UUID serverId, UUID photoSendId, long photoFinalId, boolean saveToFile) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<PhotoIdAssignedPacket> type;

	public PhotoIdAssignedPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readUUID(), buf.readLong(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(serverId);
		buf.writeUUID(photoSendId);
		buf.writeLong(photoFinalId);
		buf.writeBoolean(saveToFile);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<PhotoIdAssignedPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<PhotoIdAssignedPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, PhotoIdAssignedPacket> reader() {
			return StreamCodec.ofMember(PhotoIdAssignedPacket::write, PhotoIdAssignedPacket::new);
		}

		@Override
		public void handle(PhotoIdAssignedPacket payload, IPayloadContext context) {
			PhotosCache.rememberServer(payload.serverId);
			PhotosCache.assignImageId(payload.serverId, payload.photoFinalId, payload.photoSendId, payload.saveToFile);
		}
	}
}

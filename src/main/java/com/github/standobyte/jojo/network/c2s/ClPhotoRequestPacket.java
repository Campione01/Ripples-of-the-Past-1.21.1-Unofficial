package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.item.polaroid.PhotosHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClPhotoRequestPacket(long photoId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClPhotoRequestPacket> type;

	public ClPhotoRequestPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readLong());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeLong(photoId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClPhotoRequestPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClPhotoRequestPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClPhotoRequestPacket> reader() {
			return StreamCodec.ofMember(ClPhotoRequestPacket::write, ClPhotoRequestPacket::new);
		}

		@Override
		public void handle(ClPhotoRequestPacket payload, IPayloadContext context) {
			if (context.player() instanceof ServerPlayer player) {
				PhotosHandler.get(player.server).requestPhoto(payload.photoId, player);
			}
		}
	}
}

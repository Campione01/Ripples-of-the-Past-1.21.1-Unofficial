package com.github.standobyte.jojo.network.c2s;

import java.nio.ByteBuffer;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.polaroid.PhotosHandler;
import com.github.standobyte.jojo.network.BatchReceiver;
import com.github.standobyte.jojo.network.BatchSender;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClPhotoSaveDataPacket(long photoId, BatchSender.Batch dataBatch) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClPhotoSaveDataPacket> type;

	public ClPhotoSaveDataPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readLong(), BatchSender.Batch.read(buf));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeLong(photoId);
		dataBatch.write(buf);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClPhotoSaveDataPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClPhotoSaveDataPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClPhotoSaveDataPacket> reader() {
			return StreamCodec.ofMember(ClPhotoSaveDataPacket::write, ClPhotoSaveDataPacket::new);
		}

		@Override
		public void handle(ClPhotoSaveDataPacket payload, IPayloadContext context) {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			PhotosHandler serverPhotos = PhotosHandler.get(player.server);
			BatchReceiver receiver = serverPhotos.getOrCreateReceiver(payload.photoId);
			try {
				ByteBuffer fullPhoto = receiver.receive(payload.dataBatch);
				serverPhotos.receivePhotoBatch(payload.photoId, fullPhoto, player.getUUID());
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().warn("Failed to receive Polaroid photo batch {}", payload.photoId, e);
			}
		}
	}
}

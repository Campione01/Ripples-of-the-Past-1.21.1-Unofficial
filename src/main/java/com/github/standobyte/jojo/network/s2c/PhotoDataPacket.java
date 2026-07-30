package com.github.standobyte.jojo.network.s2c;

import java.util.UUID;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.polaroid.PhotosCache;
import com.github.standobyte.jojo.client.polaroid.PhotosCache.PhotoHolder;
import com.github.standobyte.jojo.network.BatchSender;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class PhotoDataPacket implements CustomPacketPayload {
	private static CustomPacketPayload.Type<PhotoDataPacket> type;
	private final UUID serverId;
	private final long photoId;
	private final boolean hasPhoto;
	private final BatchSender.Batch dataBatch;

	public PhotoDataPacket(UUID serverId, long photoId, BatchSender.Batch dataBatch) {
		this(serverId, photoId, true, dataBatch);
	}

	private PhotoDataPacket(UUID serverId, long photoId, boolean hasPhoto, BatchSender.Batch dataBatch) {
		this.serverId = serverId;
		this.photoId = photoId;
		this.hasPhoto = hasPhoto;
		this.dataBatch = dataBatch;
	}

	public PhotoDataPacket(RegistryFriendlyByteBuf buf) {
		this.serverId = buf.readUUID();
		this.photoId = buf.readLong();
		this.hasPhoto = buf.readBoolean();
		this.dataBatch = hasPhoto ? BatchSender.Batch.read(buf) : null;
	}

	public static PhotoDataPacket failed(UUID serverId, long photoId) {
		return new PhotoDataPacket(serverId, photoId, false, null);
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(serverId);
		buf.writeLong(photoId);
		buf.writeBoolean(hasPhoto);
		if (hasPhoto) {
			dataBatch.write(buf);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<PhotoDataPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<PhotoDataPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, PhotoDataPacket> reader() {
			return StreamCodec.ofMember(PhotoDataPacket::write, PhotoDataPacket::new);
		}

		@Override
		public void handle(PhotoDataPacket payload, IPayloadContext context) {
			PhotosCache.rememberServer(payload.serverId);
			PhotoHolder photoHolder = PhotosCache.getPhotoHolder(payload.serverId, payload.photoId);
			if (photoHolder != null) {
				photoHolder.readBatchFromPacket(payload.hasPhoto ? payload.dataBatch : null);
			}
		}
	}
}

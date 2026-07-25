package com.github.standobyte.jojo.item.polaroid;

import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.network.BatchSender;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public class PolaroidPhotoData extends SavedData {
	private byte[] photoBytes = new byte[0];
	@Nullable private UUID senderPlayer;

	public PolaroidPhotoData() {}

	public PolaroidPhotoData(byte[] photoBytes, @Nullable UUID senderPlayer) {
		this.photoBytes = photoBytes;
		this.senderPlayer = senderPlayer;
	}

	public boolean hasPhoto() {
		return photoBytes.length > 0;
	}

	public void sendTo(ServerPlayer player, UUID serverId, long photoId) {
		BatchSender sender = new SrvPhotoSender(photoBytes, serverId, photoId, player);
		sender.sendAll();
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putByteArray("Photo", photoBytes);
		if (senderPlayer != null) {
			tag.putUUID("Sender", senderPlayer);
		}
		return tag;
	}

	public static PolaroidPhotoData load(CompoundTag tag, HolderLookup.Provider registries) {
		PolaroidPhotoData data = new PolaroidPhotoData();
		data.photoBytes = tag.getByteArray("Photo");
		data.senderPlayer = tag.hasUUID("Sender") ? tag.getUUID("Sender") : null;
		return data;
	}
}

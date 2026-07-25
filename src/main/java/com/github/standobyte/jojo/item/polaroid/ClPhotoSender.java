package com.github.standobyte.jojo.item.polaroid;

import java.util.UUID;

import com.github.standobyte.jojo.network.BatchSender;
import com.github.standobyte.jojo.network.c2s.ClPhotoSaveDataPacket;

import net.neoforged.neoforge.network.PacketDistributor;

public class ClPhotoSender extends BatchSender {
	private final long photoId;

	public ClPhotoSender(byte[] data, UUID serverId, long photoId) {
		super(data);
		this.photoId = photoId;
	}

	@Override
	protected void sendBatch(Batch dataBatch) {
		PacketDistributor.sendToServer(new ClPhotoSaveDataPacket(photoId, dataBatch));
	}
}

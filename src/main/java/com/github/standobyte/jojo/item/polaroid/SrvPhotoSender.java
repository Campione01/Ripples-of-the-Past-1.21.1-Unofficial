package com.github.standobyte.jojo.item.polaroid;

import java.util.UUID;

import com.github.standobyte.jojo.network.BatchSender;
import com.github.standobyte.jojo.network.s2c.PhotoDataPacket;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class SrvPhotoSender extends BatchSender {
	private final UUID serverId;
	private final long photoId;
	private final ServerPlayer player;

	public SrvPhotoSender(byte[] data, UUID serverId, long photoId, ServerPlayer player) {
		super(data);
		this.serverId = serverId;
		this.photoId = photoId;
		this.player = player;
	}

	@Override
	protected void sendBatch(Batch dataBatch) {
		PacketDistributor.sendToPlayer(player, new PhotoDataPacket(serverId, photoId, dataBatch));
	}
}

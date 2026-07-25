package com.github.standobyte.jojo.item.polaroid;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.network.BatchReceiver;
import com.github.standobyte.jojo.network.s2c.PhotoDataPacket;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.network.PacketDistributor;

public class PhotosHandler {
	private static final Map<MinecraftServer, PhotosHandler> HANDLERS = new WeakHashMap<>();

	private final MinecraftServer server;
	private final Long2ObjectMap<BatchReceiver> photosReceiver = new Long2ObjectArrayMap<>();
	private final Long2ObjectMap<Set<ServerPlayer>> playersRequestedEarly = new Long2ObjectArrayMap<>();

	public PhotosHandler(MinecraftServer server) {
		this.server = server;
	}

	public static PhotosHandler get(MinecraftServer server) {
		return HANDLERS.computeIfAbsent(server, PhotosHandler::new);
	}

	public long incPolaroidPhotoId() {
		return ServerSavedData.get(server).incPolaroidPhotoId();
	}

	public UUID serverId() {
		return ServerSavedData.get(server).getServerUUID();
	}

	public BatchReceiver getOrCreateReceiver(long photoId) {
		return photosReceiver.computeIfAbsent(photoId, n -> new BatchReceiver());
	}

	public void receivePhotoBatch(long photoId, ByteBuffer fullPhoto, UUID photoSender) {
		if (fullPhoto != null) {
			putPhoto(photoId, BatchReceiver.byteBufferToArray(fullPhoto), photoSender);
			photosReceiver.remove(photoId);
		}
	}

	public void putPhoto(long id, byte[] photoData, UUID photoSender) {
		DimensionDataStorage storage = server.overworld().getDataStorage();
		PolaroidPhotoData photo = new PolaroidPhotoData(photoData, photoSender);
		photo.setDirty();
		storage.set(makePhotoId(id), photo);

		Set<ServerPlayer> earlyRequested = playersRequestedEarly.remove(id);
		if (earlyRequested != null) {
			UUID serverId = serverId();
			for (ServerPlayer player : earlyRequested) {
				if (!player.hasDisconnected()) {
					photo.sendTo(player, serverId, id);
				}
			}
		}
	}

	public void requestPhoto(long photoId, ServerPlayer player) {
		DimensionDataStorage storage = server.overworld().getDataStorage();
		PolaroidPhotoData photo = storage.get(new SavedData.Factory<>(
				PolaroidPhotoData::new, PolaroidPhotoData::load), makePhotoId(photoId));
		UUID serverId = serverId();
		if (photo != null && photo.hasPhoto()) {
			photo.sendTo(player, serverId, photoId);
		}
		else {
			playersRequestedEarly.computeIfAbsent(photoId, num -> new HashSet<>()).add(player);
			PacketDistributor.sendToPlayer(player, PhotoDataPacket.failed(serverId, photoId));
		}
	}

	private static String makePhotoId(long id) {
		return "jojo_photo" + id;
	}
}

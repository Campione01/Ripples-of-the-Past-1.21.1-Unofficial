package com.github.standobyte.jojo.item.polaroid;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import com.github.standobyte.jojo.ServerSavedData;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.network.BatchReceiver;
import com.github.standobyte.jojo.network.BatchSender;
import com.github.standobyte.jojo.network.s2c.PhotoDataPacket;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class PhotosHandler {
	private static final long PHOTO_PERMIT_TICKS = 20 * 30;
	private static final long PHOTO_UPLOAD_TIMEOUT_TICKS = 20 * 30;
	private static final Map<MinecraftServer, PhotosHandler> HANDLERS = new WeakHashMap<>();

	private final MinecraftServer server;
	private final PhotoSessionRegistry<ServerPlayer> sessions = new PhotoSessionRegistry<>();

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

	public boolean authorizePhotoUpload(ServerPlayer player) {
		return authorizePhotoUpload(player, player);
	}

	public boolean authorizePhotoUpload(ServerPlayer uploader, ServerPlayer target) {
		if (uploader.server != server || target.server != server
				|| uploader.hasDisconnected() || target.hasDisconnected()) {
			return false;
		}
		long gameTime = gameTime();
		cleanupExpiredUploads(gameTime);
		return sessions.authorize(uploader.getUUID(), target.getUUID(), target.getId(),
				gameTime + PHOTO_PERMIT_TICKS);
	}

	public Optional<PhotoUploadReservation> reservePhotoUpload(
			ServerPlayer uploader, int requestedTargetEntityId) {
		long gameTime = gameTime();
		cleanupExpiredUploads(gameTime);
		UUID uploaderId = uploader.getUUID();
		Optional<PhotoPermit> permit = sessions.consumePermit(
				uploaderId, requestedTargetEntityId, gameTime);
		if (permit.isEmpty()) {
			return Optional.empty();
		}

		ServerPlayer target = server.getPlayerList().getPlayer(permit.get().target());
		if (target == null || target.hasDisconnected()
				|| target.getId() != requestedTargetEntityId
				|| target.serverLevel() != uploader.serverLevel()) {
			return Optional.empty();
		}

		long photoId = incPolaroidPhotoId();
		PhotoUpload upload = new PhotoUpload(
				uploaderId, target.getUUID(),
				gameTime + PHOTO_UPLOAD_TIMEOUT_TICKS, new BatchReceiver());
		sessions.startUpload(photoId, upload);
		return Optional.of(new PhotoUploadReservation(
				photoId, target, uploaderId.equals(target.getUUID())));
	}

	public void receivePhotoBatch(long photoId, BatchSender.Batch batch, ServerPlayer sender) {
		long gameTime = gameTime();
		cleanupExpiredUploads(gameTime);
		PhotoUpload upload = sessions.getUpload(photoId);
		if (upload == null || !upload.accepts(sender.getUUID(), gameTime)) {
			throw new IllegalStateException("No matching Polaroid upload");
		}

		try {
			ByteBuffer fullPhoto = upload.receiver().receive(batch);
			if (fullPhoto != null) {
				sessions.finishUpload(photoId, upload, false);
				putPhoto(photoId, BatchReceiver.byteBufferToArray(fullPhoto), sender.getUUID());
			}
		}
		catch (RuntimeException e) {
			sessions.finishUpload(photoId, upload, true);
			throw e;
		}
	}

	public void putPhoto(long id, byte[] photoData, UUID photoSender) {
		DimensionDataStorage storage = server.overworld().getDataStorage();
		PolaroidPhotoData photo = new PolaroidPhotoData(photoData, photoSender);
		photo.setDirty();
		storage.set(makePhotoId(id), photo);

		Set<ServerPlayer> earlyRequested = sessions.takeEarlyRequests(id);
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
		long gameTime = gameTime();
		cleanupExpiredUploads(gameTime);
		if (!isKnownPhotoId(photoId, ServerSavedData.get(server).getPolaroidPhotoId())) {
			PacketDistributor.sendToPlayer(player, PhotoDataPacket.failed(serverId(), photoId));
			return;
		}

		DimensionDataStorage storage = server.overworld().getDataStorage();
		PolaroidPhotoData photo = storage.get(new SavedData.Factory<>(
				PolaroidPhotoData::new, PolaroidPhotoData::load), makePhotoId(photoId));
		UUID serverId = serverId();
		if (photo != null && photo.hasPhoto()) {
			photo.sendTo(player, serverId, photoId);
		}
		else {
			if (sessions.hasUpload(photoId)) {
				sessions.addEarlyRequest(photoId, player);
			}
			PacketDistributor.sendToPlayer(player, PhotoDataPacket.failed(serverId, photoId));
		}
	}

	private void cleanupExpiredUploads(long gameTime) {
		sessions.cleanupExpired(gameTime);
	}

	private long gameTime() {
		return server.overworld().getGameTime();
	}

	private static String makePhotoId(long id) {
		return "jojo_photo" + id;
	}

	static boolean isKnownPhotoId(long photoId, long highestAssignedPhotoId) {
		return photoId > 0 && photoId <= highestAssignedPhotoId;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		PhotosHandler handler = HANDLERS.get(event.getServer());
		if (handler != null) {
			handler.cleanupExpiredUploads(handler.gameTime());
		}
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			PhotosHandler handler = HANDLERS.get(player.server);
			if (handler != null) {
				UUID playerId = player.getUUID();
				handler.sessions.removePlayer(playerId,
						requester -> requester == player
								|| requester.getUUID().equals(playerId));
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		PhotosHandler handler = HANDLERS.remove(event.getServer());
		if (handler != null) {
			handler.sessions.clear();
		}
	}

	public static record PhotoUploadReservation(
			long photoId, ServerPlayer target, boolean saveToUploaderFile) {}

	static record PhotoPermit(UUID target, int targetEntityId, long expiresAt) {
		boolean accepts(int requestedTargetEntityId, long gameTime) {
			return targetEntityId == requestedTargetEntityId && expiresAt >= gameTime;
		}
	}

	static record PhotoUpload(
			UUID owner, UUID target, long expiresAt, BatchReceiver receiver) {
		boolean accepts(UUID sender, long gameTime) {
			return owner.equals(sender) && expiresAt >= gameTime;
		}
	}

	static final class PhotoSessionRegistry<P> {
		private final Map<UUID, PhotoPermit> permits = new HashMap<>();
		private final Map<UUID, Long> activeUploadsByPlayer = new HashMap<>();
		private final Long2ObjectMap<PhotoUpload> uploads = new Long2ObjectArrayMap<>();
		private final Long2ObjectMap<Set<P>> earlyRequests = new Long2ObjectArrayMap<>();

		boolean authorize(
				UUID uploader, UUID target, int targetEntityId, long expiresAt) {
			if (permits.containsKey(uploader)
					|| activeUploadsByPlayer.containsKey(uploader)) {
				return false;
			}
			permits.put(uploader, new PhotoPermit(target, targetEntityId, expiresAt));
			return true;
		}

		Optional<PhotoPermit> consumePermit(
				UUID uploader, int requestedTargetEntityId, long gameTime) {
			PhotoPermit permit = permits.remove(uploader);
			if (permit == null || activeUploadsByPlayer.containsKey(uploader)
					|| !permit.accepts(requestedTargetEntityId, gameTime)) {
				return Optional.empty();
			}
			return Optional.of(permit);
		}

		void startUpload(long photoId, PhotoUpload upload) {
			if (uploads.containsKey(photoId)
					|| activeUploadsByPlayer.putIfAbsent(upload.owner(), photoId) != null) {
				throw new IllegalStateException("Polaroid upload is already active");
			}
			uploads.put(photoId, upload);
		}

		PhotoUpload getUpload(long photoId) {
			return uploads.get(photoId);
		}

		boolean hasUpload(long photoId) {
			return uploads.containsKey(photoId);
		}

		void addEarlyRequest(long photoId, P requester) {
			earlyRequests.computeIfAbsent(photoId, ignored -> new HashSet<>()).add(requester);
		}

		Set<P> takeEarlyRequests(long photoId) {
			return earlyRequests.remove(photoId);
		}

		void finishUpload(
				long photoId, PhotoUpload upload, boolean discardEarlyRequests) {
			if (uploads.get(photoId) == upload) {
				uploads.remove(photoId);
				activeUploadsByPlayer.remove(upload.owner(), photoId);
			}
			if (discardEarlyRequests) {
				earlyRequests.remove(photoId);
			}
		}

		void cleanupExpired(long gameTime) {
			permits.entrySet().removeIf(
					entry -> entry.getValue().expiresAt() < gameTime);
			Iterator<Long2ObjectMap.Entry<PhotoUpload>> iterator =
					uploads.long2ObjectEntrySet().iterator();
			while (iterator.hasNext()) {
				Long2ObjectMap.Entry<PhotoUpload> entry = iterator.next();
				PhotoUpload upload = entry.getValue();
				if (upload.expiresAt() < gameTime) {
					long photoId = entry.getLongKey();
					iterator.remove();
					activeUploadsByPlayer.remove(upload.owner(), photoId);
					earlyRequests.remove(photoId);
				}
			}
		}

		void removePlayer(UUID playerId, Predicate<P> matchesRequester) {
			permits.entrySet().removeIf(entry ->
					entry.getKey().equals(playerId)
							|| entry.getValue().target().equals(playerId));

			Iterator<Long2ObjectMap.Entry<PhotoUpload>> uploadIterator =
					uploads.long2ObjectEntrySet().iterator();
			while (uploadIterator.hasNext()) {
				Long2ObjectMap.Entry<PhotoUpload> entry = uploadIterator.next();
				PhotoUpload upload = entry.getValue();
				if (upload.owner().equals(playerId) || upload.target().equals(playerId)) {
					long photoId = entry.getLongKey();
					uploadIterator.remove();
					activeUploadsByPlayer.remove(upload.owner(), photoId);
					earlyRequests.remove(photoId);
				}
			}

			Iterator<Long2ObjectMap.Entry<Set<P>>> requestIterator =
					earlyRequests.long2ObjectEntrySet().iterator();
			while (requestIterator.hasNext()) {
				Set<P> requesters = requestIterator.next().getValue();
				requesters.removeIf(matchesRequester);
				if (requesters.isEmpty()) {
					requestIterator.remove();
				}
			}
		}

		void clear() {
			permits.clear();
			activeUploadsByPlayer.clear();
			uploads.clear();
			earlyRequests.clear();
		}

		int permitCount() {
			return permits.size();
		}

		int uploadCount() {
			return uploads.size();
		}

		boolean hasActiveUpload(UUID uploader) {
			return activeUploadsByPlayer.containsKey(uploader);
		}

		int earlyRequestCount(long photoId) {
			Set<P> requesters = earlyRequests.get(photoId);
			return requesters != null ? requesters.size() : 0;
		}
	}
}

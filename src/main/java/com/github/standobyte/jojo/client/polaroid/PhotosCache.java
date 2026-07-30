package com.github.standobyte.jojo.client.polaroid;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.polaroid.ClPhotoSender;
import com.github.standobyte.jojo.network.BatchReceiver;
import com.github.standobyte.jojo.network.BatchSender;
import com.github.standobyte.jojo.network.c2s.ClPhotoAssignIdPacket;
import com.github.standobyte.jojo.network.c2s.ClPhotoRequestPacket;
import com.mojang.blaze3d.platform.NativeImage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = JojoMod.MOD_ID, value = Dist.CLIENT)
public class PhotosCache {
	static final int PHOTO_ASSIGNMENT_TIMEOUT_TICKS = 20 * 30;
	private static final UUID UNKNOWN_SERVER = new UUID(0L, 0L);
	private static final Map<UUID, Long2ObjectMap<PhotoHolder>> PHOTOS_CACHE = new HashMap<>();
	private static final Map<UUID, SendPhotoToServer> TO_SEND = new HashMap<>();
	private static UUID currentServerId = UNKNOWN_SERVER;

	public static UUID currentServerId() {
		return currentServerId;
	}

	public static void rememberServer(UUID serverId) {
		if (serverId != null && !UNKNOWN_SERVER.equals(serverId)) {
			currentServerId = serverId;
		}
	}

	static void queueToSendToServer(NativeImage photo, NativeImage highQuality, int giveToPlayer) {
		UUID tmpUuid = UUID.randomUUID();
		SendPhotoToServer send = new SendPhotoToServer(tmpUuid, photo, highQuality, giveToPlayer);
		TO_SEND.put(tmpUuid, send);
		PacketDistributor.sendToServer(new ClPhotoAssignIdPacket(send.tmpUuid, send.giveItemToPlayer));
	}

	public static void tick() {
		Iterator<SendPhotoToServer> senders = TO_SEND.values().iterator();
		while (senders.hasNext()) {
			SendPhotoToServer sender = senders.next();
			if (sender.tick()) {
				sender.close();
				senders.remove();
			}
		}
		PHOTOS_CACHE.values().forEach(photos -> photos.values().forEach(PhotoHolder::tick));
	}

	static PhotoHolder cacheImage(UUID serverId, long photoId, NativeImage image) {
		rememberServer(serverId);
		PhotoInstance photo = PhotoInstance.create(image, serverId, photoId);
		PhotoHolder photoHolder = new PhotoHolder(serverId, photoId, photo);
		PHOTOS_CACHE.computeIfAbsent(serverId, id -> new Long2ObjectOpenHashMap<>()).put(photoId, photoHolder);
		return photoHolder;
	}

	public static void assignImageId(UUID serverId, long photoId, UUID usedTmpId, boolean saveToFile) {
		rememberServer(serverId);
		SendPhotoToServer sent = TO_SEND.get(usedTmpId);
		if (sent != null && !sent.photoTransferredToCache) {
			PhotoHolder photo = cacheImage(serverId, photoId, sent.photo);
			sent.photoTransferredToCache = true;
			if (saveToFile) {
				photo.saveToFile();
			}
			sent.sendPhotoToServer(serverId, photoId);
		}
	}

	private static File getPhotosFolder(UUID serverId) {
		Minecraft mc = Minecraft.getInstance();
		return new File(mc.gameDirectory, "jojo_polaroid/" + serverId.toString());
	}

	private static File makePhotoFile(File folder, long photoId) {
		folder.mkdirs();
		return new File(folder, photoId + ".png");
	}

	public static PhotoHolder getOrCreatePhotoHolder(UUID serverId, long photoId) {
		rememberServer(serverId);
		return PHOTOS_CACHE.computeIfAbsent(serverId, id -> new Long2ObjectOpenHashMap<>())
				.computeIfAbsent(photoId, id -> new PhotoHolder(serverId, id));
	}

	@Nullable
	public static PhotoHolder getPhotoHolder(UUID serverId, long photoId) {
		Long2ObjectMap<PhotoHolder> photos = PHOTOS_CACHE.get(serverId);
		return photos != null ? photos.get(photoId) : null;
	}

	@Nullable
	public static PhotoInstance getOrTryLoadPhoto(UUID serverId, long photoId) {
		if (serverId == null || UNKNOWN_SERVER.equals(serverId)) {
			return null;
		}
		PhotoHolder photo = getOrCreatePhotoHolder(serverId, photoId);
		photo.tryLoad();
		return photo.photoInstance;
	}

	@Nullable
	public static PhotoHolder.Status getCacheStatus(UUID serverId, long photoId) {
		if (serverId == null || UNKNOWN_SERVER.equals(serverId)) {
			return null;
		}
		PhotoHolder photo = getOrCreatePhotoHolder(serverId, photoId);
		return photo.status;
	}

	public static void onLogOut(UUID serverId) {
		TO_SEND.values().forEach(SendPhotoToServer::close);
		TO_SEND.clear();
		if (serverId != null) {
			Long2ObjectMap<PhotoHolder> photos = PHOTOS_CACHE.get(serverId);
			if (photos != null) {
				Iterator<PhotoHolder> iter = photos.values().iterator();
				while (iter.hasNext()) {
					PhotoHolder photo = iter.next();
					if (photo.status == PhotoHolder.Status.FAILED) {
						iter.remove();
					}
				}
			}
		}
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		onLogOut(currentServerId);
	}

	private static NativeImage imageFromHeapBuffer(ByteBuffer input) throws IOException {
		input.rewind();
		byte[] data = new byte[input.remaining()];
		input.get(data);
		try (InputStream stream = new ByteArrayInputStream(data)) {
			return NativeImage.read(stream);
		}
	}

	static final class PendingAssignmentTimeout {
		private int ticks;

		boolean tick() {
			return ++ticks >= PHOTO_ASSIGNMENT_TIMEOUT_TICKS;
		}
	}

	private static class SendPhotoToServer {
		private final UUID tmpUuid;
		private final NativeImage photo;
		private final NativeImage highQuality;
		private final int giveItemToPlayer;
		private final PendingAssignmentTimeout assignmentTimeout =
				new PendingAssignmentTimeout();
		private boolean photoTransferredToCache;
		@Nullable private ClPhotoSender photoSender;

		private SendPhotoToServer(UUID tmpUuid, NativeImage photo, NativeImage highQuality, int giveItemToPlayer) {
			this.tmpUuid = tmpUuid;
			this.photo = photo;
			this.highQuality = highQuality;
			this.giveItemToPlayer = giveItemToPlayer;
		}

		public void sendPhotoToServer(UUID serverId, long photoId) {
			try {
				byte[] data = photo.asByteArray();
				this.photoSender = new ClPhotoSender(data, serverId, photoId);
			}
			catch (IOException e) {
				JojoMod.getLogger().error("Failed to encode Polaroid photo", e);
			}
		}

		public boolean tick() {
			if (photoSender != null) {
				photoSender.sendNext();
				return photoSender.finishedSending();
			}
			return photoTransferredToCache || assignmentTimeout.tick();
		}

		void close() {
			if (!photoTransferredToCache) {
				photo.close();
			}
			highQuality.close();
		}
	}

	public static class PhotoHolder {
		private final UUID serverUuid;
		private final long photoId;
		@Nullable public PhotoInstance photoInstance;
		@Nonnull private Status status;
		private BatchReceiver dataReceive;
		private ByteBuffer fullDataReceived;

		private PhotoHolder(UUID serverUuid, long photoId) {
			this.serverUuid = serverUuid;
			this.photoId = photoId;
			this.status = Status.EMPTY;
		}

		private PhotoHolder(UUID serverUuid, long photoId, PhotoInstance photoInstance) {
			this(serverUuid, photoId);
			if (photoInstance != null) {
				this.photoInstance = photoInstance;
				this.status = Status.CACHED;
			}
		}

		private void tryLoad() {
			switch (status) {
			case EMPTY -> {
				status = Status.LOADING_FILE;
				File photoFile = makePhotoFile(getPhotosFolder(serverUuid), photoId);
				if (photoFile.isFile()) {
					try (InputStream inputStream = new FileInputStream(photoFile)) {
						NativeImage nativeImage = NativeImage.read(inputStream);
						photoInstance = PhotoInstance.create(nativeImage, serverUuid, photoId);
						status = Status.CACHED;
					}
					catch (Throwable throwable) {
						JojoMod.getLogger().error("Could not load Polaroid photo {}_{}", serverUuid, photoId, throwable);
						photoFile.delete();
						requestFromServer();
					}
				}
				else {
					requestFromServer();
				}
			}
			case RECEIVED_FULL_FROM_SERVER -> {
				try {
					NativeImage image = imageFromHeapBuffer(fullDataReceived);
					photoInstance = PhotoInstance.create(image, serverUuid, photoId);
					status = Status.CACHED;
					saveToFile();
				}
				catch (Exception e) {
					JojoMod.getLogger().error("Could not decode Polaroid photo {}_{}", serverUuid, photoId, e);
					setFailed();
				}
			}
			default -> {
			}
			}
		}

		private void requestFromServer() {
			status = Status.REQUESTED_FROM_SERVER;
			dataReceive = new BatchReceiver();
			PacketDistributor.sendToServer(new ClPhotoRequestPacket(photoId));
		}

		public void readBatchFromPacket(BatchSender.Batch data) {
			switch (status) {
			case REQUESTED_FROM_SERVER, RECEIVING_FROM_SERVER, FAILED -> {
				if (data != null) {
					status = Status.RECEIVING_FROM_SERVER;
					try {
						ByteBuffer fullPhoto = dataReceive.receive(data);
						if (fullPhoto != null) {
							if (fullPhoto.capacity() > 0) {
								fullDataReceived = fullPhoto;
								status = Status.RECEIVED_FULL_FROM_SERVER;
							}
							else {
								setFailed();
							}
						}
					}
					catch (RuntimeException e) {
						setFailed();
					}
				}
				else {
					setFailed();
				}
			}
			default -> {
			}
			}
		}

		public void saveToFile() {
			if (photoInstance != null) {
				File photoFile = makePhotoFile(getPhotosFolder(serverUuid), photoId);
				try {
					photoInstance.image.writeToFile(photoFile);
				}
				catch (IOException e) {
					JojoMod.getLogger().error("Failed to save Polaroid photo", e);
				}
			}
		}

		private void setFailed() {
			dataReceive = new BatchReceiver();
			status = Status.FAILED;
		}

		void tick() {}

		public enum Status {
			EMPTY,
			LOADING_FILE,
			REQUESTED_FROM_SERVER,
			RECEIVING_FROM_SERVER,
			RECEIVED_FULL_FROM_SERVER,
			CACHED,
			FAILED
		}
	}

	public static class PhotoInstance {
		public final NativeImage image;
		public final DynamicTexture texture;
		public final RenderType renderType;

		public static PhotoInstance create(NativeImage image, UUID serverUuid, long photoId) {
			DynamicTexture texture = new DynamicTexture(image);
			ResourceLocation path = JojoMod.resLoc(String.format("dynamic/jojo_photo_%d_%d", serverUuid.hashCode(), photoId));
			Minecraft.getInstance().getTextureManager().register(path, texture);
			RenderType renderType = RenderType.text(path);
			return new PhotoInstance(image, texture, renderType);
		}

		private PhotoInstance(NativeImage image, DynamicTexture texture, RenderType renderType) {
			this.image = image;
			this.texture = texture;
			this.renderType = renderType;
		}
	}
}

package com.github.standobyte.jojo.network.c2s;

import java.util.Optional;
import java.util.UUID;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.PhotoItem;
import com.github.standobyte.jojo.item.polaroid.PhotosHandler;
import com.github.standobyte.jojo.item.polaroid.PhotosHandler.PhotoUploadReservation;
import com.github.standobyte.jojo.network.s2c.PhotoIdAssignedPacket;
import com.github.standobyte.jojo.util.functions.ItemUtil;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClPhotoAssignIdPacket(UUID photoUuid, int giveItemToPlayer) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClPhotoAssignIdPacket> type;

	public ClPhotoAssignIdPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readUUID(), buf.readInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeUUID(photoUuid);
		buf.writeInt(giveItemToPlayer);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClPhotoAssignIdPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClPhotoAssignIdPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClPhotoAssignIdPacket> reader() {
			return StreamCodec.ofMember(ClPhotoAssignIdPacket::write, ClPhotoAssignIdPacket::new);
		}

		@Override
		public void handle(ClPhotoAssignIdPacket payload, IPayloadContext context) {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			PhotosHandler serverPhotos = PhotosHandler.get(player.server);
			Optional<PhotoUploadReservation> reservation =
					serverPhotos.reservePhotoUpload(player, payload.giveItemToPlayer);
			if (reservation.isEmpty()) {
				return;
			}
			PhotoUploadReservation assignment = reservation.get();
			long photoId = assignment.photoId();
			PacketDistributor.sendToPlayer(player, new PhotoIdAssignedPacket(
					serverPhotos.serverId(), payload.photoUuid, photoId,
					assignment.saveToUploaderFile()));

			ItemStack photo = new ItemStack(ModItems.PHOTO.get());
			PhotoItem.setPhotoId(photo, photoId);
			PhotoItem.setPhotoAnimTicks(photo);
			ItemUtil.giveItemTo(assignment.target(), photo, true);
		}
	}
}

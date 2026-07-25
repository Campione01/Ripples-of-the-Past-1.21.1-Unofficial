package com.github.standobyte.jojo.network.c2s;

import java.util.Optional;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.WalkmanItem;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.cassette.CassetteSide;
import com.github.standobyte.jojo.item.cassette.WalkmanMenu;
import com.github.standobyte.jojo.item.cassette.WalkmanPlaybackMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClWalkmanControlsPacket(InteractionHand hand, int walkmanId, Action action, float volume, CassetteSide side, int track) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClWalkmanControlsPacket> type;

	private ClWalkmanControlsPacket(InteractionHand hand, Action action, float volume) {
		this(hand, -1, action, volume, CassetteSide.SIDE_A, 0);
	}

	public static ClWalkmanControlsPacket play(InteractionHand hand) {
		return new ClWalkmanControlsPacket(hand, Action.PLAY, -1.0F);
	}

	public static ClWalkmanControlsPacket stop(InteractionHand hand) {
		return new ClWalkmanControlsPacket(hand, Action.STOP, -1.0F);
	}

	public static ClWalkmanControlsPacket rewind(InteractionHand hand) {
		return new ClWalkmanControlsPacket(hand, Action.REWIND, -1.0F);
	}

	public static ClWalkmanControlsPacket flip(InteractionHand hand) {
		return new ClWalkmanControlsPacket(hand, Action.FLIP, -1.0F);
	}

	public static ClWalkmanControlsPacket toggleLoop(InteractionHand hand) {
		return new ClWalkmanControlsPacket(hand, Action.TOGGLE_LOOP, -1.0F);
	}

	public static ClWalkmanControlsPacket volume(InteractionHand hand, float volume) {
		return new ClWalkmanControlsPacket(hand, Action.VOLUME, volume);
	}

	public static ClWalkmanControlsPacket position(InteractionHand hand, CassetteSide side, int track) {
		return new ClWalkmanControlsPacket(hand, -1, Action.POSITION, -1.0F, side, track);
	}

	public static ClWalkmanControlsPacket position(int walkmanId, CassetteSide side, int track) {
		return new ClWalkmanControlsPacket(InteractionHand.MAIN_HAND, walkmanId, Action.POSITION, -1.0F, side, track);
	}

	public ClWalkmanControlsPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readEnum(InteractionHand.class), buf.readVarInt(), buf.readEnum(Action.class), buf.readFloat(), buf.readEnum(CassetteSide.class), buf.readVarInt());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeEnum(hand);
		buf.writeVarInt(walkmanId);
		buf.writeEnum(action);
		buf.writeFloat(volume);
		buf.writeEnum(side);
		buf.writeVarInt(track);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	public enum Action {
		PLAY,
		STOP,
		REWIND,
		FLIP,
		TOGGLE_LOOP,
		VOLUME,
		POSITION
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClWalkmanControlsPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClWalkmanControlsPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClWalkmanControlsPacket> reader() {
			return StreamCodec.ofMember(ClWalkmanControlsPacket::write, ClWalkmanControlsPacket::new);
		}

		@Override
		public void handle(ClWalkmanControlsPacket payload, IPayloadContext context) {
			Player player = context.player();
			ItemStack walkman = findWalkman(player, payload).orElse(ItemStack.EMPTY);
			if (!walkman.is(ModItems.WALKMAN.get())) {
				return;
			}

			WalkmanItem.editWalkmanData(walkman, data -> switch (payload.action) {
				case VOLUME -> data.withVolume(payload.volume);
				case TOGGLE_LOOP -> data.withPlaybackMode(data.playbackMode().toggle());
				default -> data;
			});

			if (payload.action == Action.FLIP || payload.action == Action.REWIND) {
				WalkmanItem.editWalkmanData(walkman, data -> {
					ItemStack cassette = data.cassette();
					if (!cassette.isEmpty()) {
						CassetteRecordedItem.editCassetteData(cassette, cassetteData -> payload.action == Action.FLIP
								? cassetteData.withSide(cassetteData.side().opposite())
								: cassetteData.withSideTrack(Math.max(0, cassetteData.sideTrack() - 1)));
						return data.withCassette(cassette);
					}
					return data;
				});
			}

			if (payload.action == Action.POSITION) {
				WalkmanItem.editWalkmanData(walkman, data -> {
					ItemStack cassette = data.cassette();
					if (!cassette.isEmpty()) {
						CassetteRecordedItem.editCassetteData(cassette, cassetteData -> cassetteData
								.withSide(payload.side)
								.withSideTrack(payload.track));
						return data.withCassette(cassette);
					}
					return data;
				});
			}
		}

		private Optional<ItemStack> findWalkman(Player player, ClWalkmanControlsPacket payload) {
			if (payload.walkmanId < 0) {
				return Optional.of(player.getItemInHand(payload.hand));
			}
			if (player.containerMenu instanceof WalkmanMenu menu && checkId(menu.getWalkmanItem(), payload.walkmanId)) {
				return Optional.of(menu.getWalkmanItem());
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack item = player.getInventory().getItem(i);
				if (checkId(item, payload.walkmanId)) {
					return Optional.of(item);
				}
			}
			return Optional.empty();
		}

		private boolean checkId(ItemStack item, int walkmanId) {
			return WalkmanItem.getWalkmanData(item)
					.map(data -> data.idInitialized() && data.id() == walkmanId)
					.orElse(false);
		}
	}
}

package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClBroadcastedModSettingsPacket(PlayerClientBroadcastedSettings settings) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClBroadcastedModSettingsPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClBroadcastedModSettingsPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClBroadcastedModSettingsPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClBroadcastedModSettingsPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, ClBroadcastedModSettingsPacket> STREAM_CODEC = StreamCodec.ofMember(
				ClBroadcastedModSettingsPacket::write, ClBroadcastedModSettingsPacket::new);

		@Override
		public void handle(ClBroadcastedModSettingsPacket payload, IPayloadContext context) {
			Player player = context.player();
			PlayerClientBroadcastedSettings authoritativeSettings = player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			authoritativeSettings.copyFrom(payload.settings);
			authoritativeSettings.syncToAll(player);
		}
	}

	public ClBroadcastedModSettingsPacket(RegistryFriendlyByteBuf buf) {
		this(new PlayerClientBroadcastedSettings());
		this.settings.fromBuf(buf);
	}

	public void write(RegistryFriendlyByteBuf buf) {
		settings.toBuf(buf);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

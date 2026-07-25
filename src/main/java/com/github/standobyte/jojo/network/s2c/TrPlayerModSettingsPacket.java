package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrPlayerModSettingsPacket(int entityId, PlayerClientBroadcastedSettings settings) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrPlayerModSettingsPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrPlayerModSettingsPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPlayerModSettingsPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrPlayerModSettingsPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrPlayerModSettingsPacket> STREAM_CODEC = StreamCodec.ofMember(
				TrPlayerModSettingsPacket::write, TrPlayerModSettingsPacket::new);

		@Override
		public void handle(TrPlayerModSettingsPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof Player player && player.isLocalPlayer()) {
				return;
			}
			PlayerClientBroadcastedSettings.putRemoteSettings(payload.entityId, payload.settings.copy());
		}
	}

	public TrPlayerModSettingsPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readInt(), new PlayerClientBroadcastedSettings());
		this.settings.fromBuf(buf);
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(entityId);
		settings.toBuf(buf);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

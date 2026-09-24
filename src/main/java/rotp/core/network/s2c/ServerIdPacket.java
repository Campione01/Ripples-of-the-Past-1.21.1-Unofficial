package rotp.core.network.s2c;

import java.util.UUID;

import rotp.core.PacketsRegister;
import rotp.core.client.polaroid.PhotosCache;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 ServerIdPacket, sent on login (SaveFileUtilCap.onPlayerLogIn): the id under which the client caches and
 * requests this server's Polaroid photos. Without it a photo someone else took stayed blank until the player
 * uploaded one of their own.
 */
public record ServerIdPacket(UUID serverId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ServerIdPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<ServerIdPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ServerIdPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ServerIdPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, ServerIdPacket> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, ServerIdPacket::serverId,
				ServerIdPacket::new);

		@Override
		public void handle(ServerIdPacket payload, IPayloadContext context) {
			PhotosCache.rememberServer(payload.serverId);
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

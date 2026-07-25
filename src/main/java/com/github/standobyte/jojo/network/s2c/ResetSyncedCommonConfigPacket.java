package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.PacketsRegister;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ResetSyncedCommonConfigPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ResetSyncedCommonConfigPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<ResetSyncedCommonConfigPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ResetSyncedCommonConfigPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ResetSyncedCommonConfigPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, ResetSyncedCommonConfigPacket> STREAM_CODEC = StreamCodec.unit(
				new ResetSyncedCommonConfigPacket());

		@Override
		public void handle(ResetSyncedCommonConfigPacket payload, IPayloadContext context) {
			JojoModConfig.resetSyncedConfig();
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.JojoModConfig;
import com.github.standobyte.jojo.PacketsRegister;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CommonConfigPacket(JojoModConfig.Common.SyncedValues values) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<CommonConfigPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<CommonConfigPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<CommonConfigPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, CommonConfigPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, CommonConfigPacket> STREAM_CODEC = StreamCodec.ofMember(
				CommonConfigPacket::write, CommonConfigPacket::new);

		@Override
		public void handle(CommonConfigPacket payload, IPayloadContext context) {
			JojoModConfig.applySyncedConfig(payload.values);
		}
	}

	public CommonConfigPacket(RegistryFriendlyByteBuf buf) {
		this(new JojoModConfig.Common.SyncedValues(buf));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		values.writeToBuf(buf);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

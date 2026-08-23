package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ui.screen_jojomenu.JojoMenuTabs;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonTeacherScreenPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonTeacherScreenPacket> packetType;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrHamonTeacherScreenPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonTeacherScreenPacket> type() {
			return packetType;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrHamonTeacherScreenPacket> reader() {
			return STREAM_CODEC;
		}

		private static final StreamCodec<RegistryFriendlyByteBuf, TrHamonTeacherScreenPacket> STREAM_CODEC =
				StreamCodec.unit(new TrHamonTeacherScreenPacket());

		@Override
		public void handle(TrHamonTeacherScreenPacket payload, IPayloadContext context) {
			JojoMenuTabs.openHamonTeacherScreen();
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

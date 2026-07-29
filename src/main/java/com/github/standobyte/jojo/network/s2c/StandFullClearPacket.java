package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StandFullClearPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<StandFullClearPacket> type;

	public static class Handler
			implements PacketsRegister.PacketCodecHandler<StandFullClearPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<StandFullClearPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, StandFullClearPacket>
				reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<
				RegistryFriendlyByteBuf,
				StandFullClearPacket> STREAM_CODEC =
						StreamCodec.unit(new StandFullClearPacket());

		@Override
		public void handle(
				StandFullClearPacket payload,
				IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				StandPower power = StandPower.get(player);
				if (power != null) {
					power.clientApplyFullStandClear();
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

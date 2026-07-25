package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonStopWallClimbPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonStopWallClimbPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonStopWallClimbPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonStopWallClimbPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonStopWallClimbPacket packet, RegistryFriendlyByteBuf buf) {}

		@Override
		public ClHamonStopWallClimbPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonStopWallClimbPacket();
		}

		@Override
		public void handle(ClHamonStopWallClimbPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (player.isAlive()) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.stopWallClimbing(player);
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

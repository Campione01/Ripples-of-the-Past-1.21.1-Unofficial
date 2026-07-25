package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonWallClimbMovementPacket(boolean moving, double movementUp,
		double movementLeft, float speed) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonWallClimbMovementPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonWallClimbMovementPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonWallClimbMovementPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonWallClimbMovementPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.moving);
			buf.writeDouble(packet.movementUp);
			buf.writeDouble(packet.movementLeft);
			buf.writeFloat(packet.speed);
		}

		@Override
		public ClHamonWallClimbMovementPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonWallClimbMovementPacket(buf.readBoolean(), buf.readDouble(),
					buf.readDouble(), buf.readFloat());
		}

		@Override
		public void handle(ClHamonWallClimbMovementPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (player.isAlive() && Double.isFinite(payload.movementUp)
					&& Double.isFinite(payload.movementLeft) && Float.isFinite(payload.speed)) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					if (hamon.isHamonWallClimbing()) {
						hamon.setWallClimbMotion(payload.moving, payload.movementUp,
								payload.movementLeft, payload.speed);
						PacketDistributor.sendToPlayersTrackingEntity(player,
								new TrHamonWallClimbMovementPacket(player.getId(), payload.moving,
										payload.movementUp, payload.movementLeft, payload.speed));
					}
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

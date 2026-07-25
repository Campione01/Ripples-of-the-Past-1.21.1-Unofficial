package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonWallClimbMovementPacket(int entityId, boolean moving,
		double movementUp, double movementLeft, float speed) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonWallClimbMovementPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonWallClimbMovementPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonWallClimbMovementPacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonWallClimbMovementPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeBoolean(packet.moving);
			buf.writeDouble(packet.movementUp);
			buf.writeDouble(packet.movementLeft);
			buf.writeFloat(packet.speed);
		}

		@Override
		public TrHamonWallClimbMovementPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrHamonWallClimbMovementPacket(buf.readInt(), buf.readBoolean(),
					buf.readDouble(), buf.readDouble(), buf.readFloat());
		}

		@Override
		public void handle(TrHamonWallClimbMovementPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof Player player) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon ->
						hamon.setWallClimbMotion(payload.moving, payload.movementUp,
								payload.movementLeft, payload.speed));
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

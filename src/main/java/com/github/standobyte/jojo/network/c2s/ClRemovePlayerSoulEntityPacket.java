package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClRemovePlayerSoulEntityPacket(int soulEntityId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClRemovePlayerSoulEntityPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClRemovePlayerSoulEntityPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClRemovePlayerSoulEntityPacket> type() {
			return type;
		}

		@Override
		public void encode(ClRemovePlayerSoulEntityPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.soulEntityId);
		}

		@Override
		public ClRemovePlayerSoulEntityPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClRemovePlayerSoulEntityPacket(buf.readInt());
		}

		@Override
		public void handle(ClRemovePlayerSoulEntityPacket payload, IPayloadContext context) {
			Player player = context.player();
			Entity entity = player.level().getEntity(payload.soulEntityId);
			if (entity instanceof SoulEntity soulEntity && soulEntity.getOriginEntity() == player) {
				soulEntity.skipAscension();
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

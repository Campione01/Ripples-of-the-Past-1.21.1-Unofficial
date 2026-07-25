package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.google.common.primitives.Floats;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClSoulRotationPacket(int entityId, float yRot, float xRot) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClSoulRotationPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClSoulRotationPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClSoulRotationPacket> type() {
			return type;
		}

		@Override
		public void encode(ClSoulRotationPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeFloat(packet.yRot);
			buf.writeFloat(packet.xRot);
		}

		@Override
		public ClSoulRotationPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClSoulRotationPacket(buf.readInt(), buf.readFloat(), buf.readFloat());
		}

		@Override
		public void handle(ClSoulRotationPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (!Floats.isFinite(payload.xRot) || !Floats.isFinite(payload.yRot)) {
				return;
			}
			Entity entity = player.level().getEntity(payload.entityId);
			if (entity instanceof SoulEntity soulEntity && soulEntity.getOriginEntity() == player) {
				soulEntity.handleRotationPacket(payload.yRot, payload.xRot);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

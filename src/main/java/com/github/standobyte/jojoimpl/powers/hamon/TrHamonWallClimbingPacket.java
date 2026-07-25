package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonWallClimbingPacket(int entityId, boolean wallClimbing, boolean hamon,
		float climbSpeed, boolean hasBodyRot, float bodyRot) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonWallClimbingPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonWallClimbingPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonWallClimbingPacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonWallClimbingPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeBoolean(packet.wallClimbing);
			buf.writeBoolean(packet.hamon);
			buf.writeFloat(packet.climbSpeed);
			buf.writeBoolean(packet.hasBodyRot);
			if (packet.hasBodyRot) {
				buf.writeFloat(packet.bodyRot);
			}
		}

		@Override
		public TrHamonWallClimbingPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			boolean wallClimbing = buf.readBoolean();
			boolean hamon = buf.readBoolean();
			float climbSpeed = buf.readFloat();
			boolean hasBodyRot = buf.readBoolean();
			float bodyRot = hasBodyRot ? buf.readFloat() : 0.0F;
			return new TrHamonWallClimbingPacket(entityId, wallClimbing, hamon, climbSpeed, hasBodyRot, bodyRot);
		}

		@Override
		public void handle(TrHamonWallClimbingPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.trSetWallClimbing(payload.wallClimbing, payload.hamon,
							payload.climbSpeed, payload.hasBodyRot, payload.bodyRot);
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

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

public record TrHamonLiquidWalkingPacket(int entityId, boolean liquidWalking) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonLiquidWalkingPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonLiquidWalkingPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonLiquidWalkingPacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonLiquidWalkingPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeBoolean(packet.liquidWalking);
		}

		@Override
		public TrHamonLiquidWalkingPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrHamonLiquidWalkingPacket(buf.readInt(), buf.readBoolean());
		}

		@Override
		public void handle(TrHamonLiquidWalkingPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.trSetWaterWalking(payload.liquidWalking);
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonEntityChargePacket(int entityId, ChargeType chargeType, boolean hasCharge, int tickCount, int maxTicks) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonEntityChargePacket> packetType;

	public static TrHamonEntityChargePacket entityCharge(int entityId, boolean hasCharge) {
		return new TrHamonEntityChargePacket(entityId, ChargeType.ENTITY, hasCharge, -1, -1);
	}

	public static TrHamonEntityChargePacket projectileCharge(int entityId, boolean hasCharge, int tickCount, int maxTicks) {
		return new TrHamonEntityChargePacket(entityId, ChargeType.PROJECTILE, hasCharge, tickCount, maxTicks);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonEntityChargePacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonEntityChargePacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonEntityChargePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeEnum(packet.chargeType);
			buf.writeInt(packet.entityId);
			buf.writeBoolean(packet.hasCharge);
			if (packet.chargeType == ChargeType.PROJECTILE && packet.hasCharge) {
				buf.writeVarInt(packet.tickCount);
				buf.writeVarInt(packet.maxTicks);
			}
		}

		@Override
		public TrHamonEntityChargePacket decode(RegistryFriendlyByteBuf buf) {
			ChargeType chargeType = buf.readEnum(ChargeType.class);
			int entityId = buf.readInt();
			boolean hasCharge = buf.readBoolean();
			return switch (chargeType) {
			case ENTITY -> entityCharge(entityId, hasCharge);
			case PROJECTILE -> {
				int ticks = hasCharge ? buf.readVarInt() : -1;
				int maxTicks = hasCharge ? buf.readVarInt() : -1;
				yield projectileCharge(entityId, hasCharge, ticks, maxTicks);
			}
			};
		}

		@Override
		public void handle(TrHamonEntityChargePacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				switch (payload.chargeType) {
				case ENTITY -> EntityHamonChargeState.get(entity).setClientHasCharge(payload.hasCharge);
				case PROJECTILE -> ProjectileHamonChargeState.handleClientPacket(entity, payload);
				}
			}
		}
	}

	public enum ChargeType {
		ENTITY,
		PROJECTILE
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

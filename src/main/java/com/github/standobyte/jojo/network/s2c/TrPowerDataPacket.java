package com.github.standobyte.jojo.network.s2c;

import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.PowerType;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TrPowerDataPacket implements CustomPacketPayload {
	private final int entityId;
	private final boolean isSentToTracking;
	private final PowerClass<?> powerClass;
	private final ResourceLocation powerTypeId;
	private PowerData serverPowerTypeData;
	private FriendlyByteBuf clientPowerTypeData;
	
	public TrPowerDataPacket(int entityId, PowerClass<?> powerClass, PowerData powerTypeData, boolean isSentToTracking) {
		this.entityId = entityId;
		this.isSentToTracking = isSentToTracking;
		this.powerClass = Objects.requireNonNull(powerClass, "powerClass");
		this.serverPowerTypeData = Objects.requireNonNull(powerTypeData, "powerTypeData");
		PowerType powerType = Objects.requireNonNull(
				powerTypeData.getPowerType(), "powerTypeData.powerType");
		if (powerTypeData.getPowerClass() != powerClass
				|| powerType.getPowerClass() != powerClass) {
			throw new IllegalArgumentException(
					"Power data type " + powerType.getId()
							+ " does not belong to " + powerClass);
		}
		this.powerTypeId = Objects.requireNonNull(
				powerType.getId(), "powerTypeData.powerType.id");
	}
	
	private TrPowerDataPacket(int entityId, PowerClass<?> powerClass,
			ResourceLocation powerTypeId, boolean isSentToTracking) {
		this.entityId = entityId;
		this.isSentToTracking = isSentToTracking;
		this.powerClass = powerClass;
		this.powerTypeId = powerTypeId;
	}
	
	
	private static CustomPacketPayload.Type<TrPowerDataPacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<TrPowerDataPacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPowerDataPacket> type() {
			return type;
		}

		@Override
		public void encode(TrPowerDataPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			PowerClass.NETWORK_CODEC.encode(buf, packet.powerClass);
			ResourceLocation.STREAM_CODEC.encode(buf, packet.powerTypeId);
			buf.writeBoolean(packet.isSentToTracking);
			if (packet.serverPowerTypeData != null) {
				packet.serverPowerTypeData.toBuf(buf, packet.isSentToTracking);
			}
		}

		@Override
		public TrPowerDataPacket decode(RegistryFriendlyByteBuf buf) {
			TrPowerDataPacket packet = new TrPowerDataPacket(
					buf.readInt(), 
					PowerClass.NETWORK_CODEC.decode(buf),
					ResourceLocation.STREAM_CODEC.decode(buf),
					buf.readBoolean());
			packet.clientPowerTypeData = NetworkUtil.extraPacketData(buf);
			return packet;
		}

		@Override
		public void handle(TrPowerDataPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PowerClass<?> powerClass = payload.powerClass;
				if (powerClass == null) {
					return;
				}
				Power<?> power = powerClass.get(living);
				if (power != null) {
					PowerType powerType = powerClass.getPowerType(
							payload.powerTypeId);
					PowerData perTypePlayerData =
							powerType != null
									&& isCompatiblePowerType(
											payload.powerTypeId,
											powerType.getId(),
											powerType.getPowerClass()
													== powerClass)
									? power.getPowerTypeData(powerType)
									: null;
					if (perTypePlayerData != null) {
						perTypePlayerData.fromBuf(payload.clientPowerTypeData, payload.isSentToTracking);
					}
				}
			}
		}

		static boolean isCompatiblePowerType(
				@Nullable ResourceLocation expectedId,
				@Nullable ResourceLocation resolvedId,
				boolean powerClassMatches) {
			return expectedId != null
					&& resolvedId != null
					&& powerClassMatches
					&& expectedId.equals(resolvedId);
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

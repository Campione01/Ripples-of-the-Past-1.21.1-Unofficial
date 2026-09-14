package rotp.core.network.s2c;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.util.functions_network.NetworkUtil;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrPowerTypePacket(
		int entityId,
		@Nullable PlayerPowerType<?> powerType,
		@Nullable PlayerPowerType<?> retainedType)
		implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrPowerTypePacket> type;

	public TrPowerTypePacket(
			int entityId,
			@Nullable PlayerPowerType<?> powerType) {
		this(entityId, powerType, null);
	}
	
	public static class Handler implements PacketsRegister.PacketCodecHandler<TrPowerTypePacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPowerTypePacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrPowerTypePacket> reader() {
			return STREAM_CODEC;
		}
		
		
		public static final StreamCodec<RegistryFriendlyByteBuf, TrPowerTypePacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrPowerTypePacket::entityId,
				NetworkUtil.nullableCodec(NetworkUtil.registryCodec(JojoRegistries.PLAYER_POWER_TYPES_REG_KEY)), TrPowerTypePacket::powerType,
				NetworkUtil.nullableCodec(NetworkUtil.registryCodec(JojoRegistries.PLAYER_POWER_TYPES_REG_KEY)), TrPowerTypePacket::retainedType,
				TrPowerTypePacket::new);

		@Override
		public void handle(TrPowerTypePacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PlayerPower power = PowerClass.PLAYER_POWER.attachGet(living);
				power.applyTrackedPowerType(
						payload.powerType(),
						payload.retainedType());
			}
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

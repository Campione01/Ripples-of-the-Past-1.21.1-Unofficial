package com.github.standobyte.jojo.powersystem.standpower.packet;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrStandLeapCooldownPacket(int entityId, int cooldown) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrStandLeapCooldownPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrStandLeapCooldownPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrStandLeapCooldownPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrStandLeapCooldownPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrStandLeapCooldownPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrStandLeapCooldownPacket::entityId,
				ByteBufCodecs.INT, TrStandLeapCooldownPacket::cooldown,
				TrStandLeapCooldownPacket::new);

		@Override
		public void handle(TrStandLeapCooldownPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				StandPower standPower = StandPower.get(living);
				if (standPower != null) {
					standPower.setLeapCooldown(payload.cooldown);
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

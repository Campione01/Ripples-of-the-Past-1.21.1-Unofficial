package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrDyingBodyTimerPacket(int entityId, int timer, int fullDuration) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrDyingBodyTimerPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrDyingBodyTimerPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrDyingBodyTimerPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrDyingBodyTimerPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrDyingBodyTimerPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrDyingBodyTimerPacket::entityId,
				ByteBufCodecs.INT, TrDyingBodyTimerPacket::timer,
				ByteBufCodecs.INT, TrDyingBodyTimerPacket::fullDuration,
				TrDyingBodyTimerPacket::new);

		@Override
		public void handle(TrDyingBodyTimerPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				JojoModLivingVariables.get(living).setDyingBodyTimer(payload.timer, payload.fullDuration);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

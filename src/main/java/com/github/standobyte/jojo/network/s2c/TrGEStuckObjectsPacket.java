package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEStuckObjectsState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrGEStuckObjectsPacket(int entityId, int stuckKnives) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrGEStuckObjectsPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrGEStuckObjectsPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrGEStuckObjectsPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrGEStuckObjectsPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrGEStuckObjectsPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrGEStuckObjectsPacket::entityId,
				ByteBufCodecs.VAR_INT, TrGEStuckObjectsPacket::stuckKnives,
				TrGEStuckObjectsPacket::new);

		@Override
		public void handle(TrGEStuckObjectsPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				GEStuckObjectsState.get(living).setStuckKnivesFromSync(payload.stuckKnives);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

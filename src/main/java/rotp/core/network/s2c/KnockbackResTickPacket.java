package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 KnockbackResTickPacket: a blocked hit's one-tick knockback resistance, set on the tracking clients too, so the
 * user's own client drops its hurt sound (NoKnockbackOnBlocking.cancelHurtSound).
 */
public record KnockbackResTickPacket(int entityId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<KnockbackResTickPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<KnockbackResTickPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<KnockbackResTickPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, KnockbackResTickPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, KnockbackResTickPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, KnockbackResTickPacket::entityId,
				KnockbackResTickPacket::new);

		@Override
		public void handle(KnockbackResTickPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				NoKnockbackOnBlocking.setOneTickKbResClient(living);
			}
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

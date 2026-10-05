package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.init.ModParticles;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** One-shot 1.16 Pillarman hit sparks at the tracked entity's client position. */
public record TrPillarmanParticlesPacket(int entityId, int quantity) implements CustomPacketPayload {

	public static void send(Entity entity, TrPillarmanParticlesPacket packet) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet);
	}

	private static CustomPacketPayload.Type<TrPillarmanParticlesPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrPillarmanParticlesPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPillarmanParticlesPacket> type() {
			return type;
		}

		@Override
		public void encode(TrPillarmanParticlesPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeInt(packet.quantity);
		}

		@Override
		public TrPillarmanParticlesPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrPillarmanParticlesPacket(buf.readInt(), buf.readInt());
		}

		@Override
		public void handle(TrPillarmanParticlesPacket payload, IPayloadContext context) {
			ClientSide.handle(payload);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	// Client symbols are resolved only when this clientbound handler is invoked.
	private static final class ClientSide {
		static void handle(TrPillarmanParticlesPacket payload) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity == null) {
				return;
			}
			for (int i = 0; i <= payload.quantity; i++) {
				entity.level().addParticle(ModParticles.LIGHT_SPARK.get(), true,
						entity.getX(), entity instanceof LivingEntity ? entity.getY() + 1.0D : entity.getY(), entity.getZ(),
						(Math.random() - 0.5D) / 3.0D,
						(Math.random() - 0.5D) / 3.0D,
						(Math.random() - 0.5D) / 3.0D);
			}
		}
	}
}

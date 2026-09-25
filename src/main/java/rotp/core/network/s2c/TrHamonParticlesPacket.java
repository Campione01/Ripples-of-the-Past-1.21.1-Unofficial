package rotp.core.network.s2c;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.init.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 TrHamonParticlesPacket.emitter: a landed Hamon hit makes the target spark for a while
 * and crackle. A null particle means the default Hamon spark.
 */
public record TrHamonParticlesPacket(int entityId, float intensity, float soundVolume, @Nullable ParticleOptions particle)
		implements CustomPacketPayload {
	public static final float MAX_INTENSITY = 4.0F;

	/** 1.16 HamonUtil.createHamonSparkParticlesEmitter server half; null when nothing would show. */
	@Nullable
	public static TrHamonParticlesPacket emitter(Entity entity, float intensity, float soundVolume, @Nullable ParticleOptions particle) {
		if (!(intensity > 0.0F)) {
			return null;
		}
		return new TrHamonParticlesPacket(entity.getId(), Math.min(intensity, MAX_INTENSITY), soundVolume,
				particle == ModParticles.HAMON_SPARK.get() ? null : particle);
	}

	/** Sends to the players tracking {@code entity} and the entity itself. */
	public static void send(Entity entity, TrHamonParticlesPacket packet) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, packet);
	}

	private static CustomPacketPayload.Type<TrHamonParticlesPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonParticlesPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonParticlesPacket> type() {
			return type;
		}

		@Override
		public void encode(TrHamonParticlesPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeFloat(packet.intensity);
			buf.writeFloat(packet.soundVolume);
			buf.writeBoolean(packet.particle != null);
			if (packet.particle != null) {
				ParticleTypes.STREAM_CODEC.encode(buf, packet.particle);
			}
		}

		@Override
		public TrHamonParticlesPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			float intensity = buf.readFloat();
			float soundVolume = buf.readFloat();
			ParticleOptions particle = buf.readBoolean() ? ParticleTypes.STREAM_CODEC.decode(buf) : null;
			return new TrHamonParticlesPacket(entityId, intensity, soundVolume, particle);
		}

		@Override
		public void handle(TrHamonParticlesPacket payload, IPayloadContext context) {
			ClientSide.handle(payload);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

	// client-only classes stay out of the common handler
	private static final class ClientSide {
		static void handle(TrHamonParticlesPacket payload) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				CustomParticlesHelper.createHamonSparkParticlesEmitter(entity, payload.intensity, payload.soundVolume,
						payload.particle != null ? payload.particle : ModParticles.HAMON_SPARK.get());
			}
		}
	}
}

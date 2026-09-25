package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.init.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 TrEntitySpecialEffectPacket: every receiver sees menacing particles on the entity,
 * only the triggering player hears the sound as non-positional music.
 */
public record TrEntitySpecialEffectPacket(int entityId, Holder<SoundEvent> sound, int playerId)
		implements CustomPacketPayload {

	/** Sends to the players tracking {@code entity} (and the entity itself if it is a player). */
	public static void send(Entity entity, Holder<SoundEvent> sound, Player triggeringPlayer) {
		PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
				new TrEntitySpecialEffectPacket(entity.getId(), sound, triggeringPlayer.getId()));
	}

	private static CustomPacketPayload.Type<TrEntitySpecialEffectPacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrEntitySpecialEffectPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrEntitySpecialEffectPacket> type() {
			return type;
		}

		@Override
		public void encode(TrEntitySpecialEffectPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			SoundEvent.STREAM_CODEC.encode(buf, packet.sound);
			buf.writeInt(packet.playerId);
		}

		@Override
		public TrEntitySpecialEffectPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrEntitySpecialEffectPacket(buf.readInt(), SoundEvent.STREAM_CODEC.decode(buf), buf.readInt());
		}

		@Override
		public void handle(TrEntitySpecialEffectPacket payload, IPayloadContext context) {
			ClientSide.handle(payload);
		}
	}

	// client-only classes stay out of the common handler
	private static final class ClientSide {
		static void handle(TrEntitySpecialEffectPacket payload) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity == null) {
				return;
			}
			Entity triggeringPlayer = ClientProxy.getEntityById(payload.playerId);
			if (triggeringPlayer != null && triggeringPlayer == ClientProxy.getClientPlayer()) {
				// 1.16 ClientUtil.playMusic: RECORDS, no attenuation, relative
				Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(
						payload.sound.value().getLocation(), SoundSource.RECORDS, 1.0F, 1.0F,
						SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE,
						0, 0, 0, true));
			}
			CustomParticlesHelper.addMenacingParticleEmitter(entity, ModParticles.MENACING.get());
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

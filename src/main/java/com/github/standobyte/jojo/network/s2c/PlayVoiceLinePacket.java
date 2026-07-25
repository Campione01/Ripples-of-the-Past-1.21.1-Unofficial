package com.github.standobyte.jojo.network.s2c;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.sound.ClientVoiceLineManager;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayVoiceLinePacket(@Nullable Holder<SoundEvent> sound, @Nullable SoundSource source, int entityId,
		float volume, float pitch, boolean interrupt) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<PlayVoiceLinePacket> type;

	public static PlayVoiceLinePacket notTriggered(int entityId) {
		return new PlayVoiceLinePacket(null, null, entityId, 0.0F, 0.0F, false);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<PlayVoiceLinePacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<PlayVoiceLinePacket> type() {
			return type;
		}

		@Override
		public void encode(PlayVoiceLinePacket packet, RegistryFriendlyByteBuf buf) {
			boolean triggerVoiceLine = packet.sound != null;
			buf.writeBoolean(triggerVoiceLine);
			buf.writeInt(packet.entityId);
			if (triggerVoiceLine) {
				SoundEvent.STREAM_CODEC.encode(buf, packet.sound);
				buf.writeEnum(packet.source);
				buf.writeFloat(packet.volume);
				buf.writeFloat(packet.pitch);
				buf.writeBoolean(packet.interrupt);
			}
		}

		@Override
		public PlayVoiceLinePacket decode(RegistryFriendlyByteBuf buf) {
			boolean triggerVoiceLine = buf.readBoolean();
			int entityId = buf.readInt();
			if (!triggerVoiceLine) {
				return PlayVoiceLinePacket.notTriggered(entityId);
			}
			return new PlayVoiceLinePacket(SoundEvent.STREAM_CODEC.decode(buf), buf.readEnum(SoundSource.class),
					entityId, buf.readFloat(), buf.readFloat(), buf.readBoolean());
		}

		@Override
		public void handle(PlayVoiceLinePacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				if (payload.sound == null) {
					ClientVoiceLineManager.voiceLineNotTriggered(entity);
				}
				else {
					ClientVoiceLineManager.playVoiceLine(entity, payload.sound.value(),
							payload.source, payload.volume, payload.pitch, payload.interrupt);
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

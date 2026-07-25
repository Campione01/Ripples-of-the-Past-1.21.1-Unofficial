package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions_network.PacketDistributor2;
import com.github.standobyte.jojo.util.functions_network.StreamCodecs;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrBarrageHitSoundPacket(int standEntityId, boolean hit, Holder<SoundEvent> sound, Vec3 soundPos)
		implements CustomPacketPayload {
	
	public TrBarrageHitSoundPacket(int standEntityId, Holder<SoundEvent> sound, Vec3 soundPos) {
		this(standEntityId, true, sound, soundPos);
	}
	
	public static TrBarrageHitSoundPacket noSound(int standEntityId) {
		return new TrBarrageHitSoundPacket(standEntityId, false, null, null);
	}
	
	public static void send(StandEntity stand, boolean playSound, Holder<SoundEvent> sound, Vec3 soundPos) {
		TrBarrageHitSoundPacket packet = playSound && sound != null && soundPos != null
				? new TrBarrageHitSoundPacket(stand.getId(), sound, soundPos)
				: noSound(stand.getId());
		PacketDistributor2.sendToPlayersTrackingEntity(stand, StandUtil::entityCanHearStands, false, packet);
	}
	
	private static CustomPacketPayload.Type<TrBarrageHitSoundPacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<TrBarrageHitSoundPacket> {
		
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrBarrageHitSoundPacket> type() {
			return type;
		}

		@Override
		public void encode(TrBarrageHitSoundPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.standEntityId);
			buf.writeBoolean(packet.hit);
			if (packet.hit) {
				SoundEvent.STREAM_CODEC.encode(buf, packet.sound);
				StreamCodecs.VEC_3D_APPROX.encode(buf, packet.soundPos);
			}
		}

		@Override
		public TrBarrageHitSoundPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			boolean hit = buf.readBoolean();
			return hit
					? new TrBarrageHitSoundPacket(entityId, SoundEvent.STREAM_CODEC.decode(buf), StreamCodecs.VEC_3D_APPROX.decode(buf))
					: TrBarrageHitSoundPacket.noSound(entityId);
		}

		@Override
		public void handle(TrBarrageHitSoundPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.standEntityId);
			if (entity instanceof StandEntity stand && stand.clientStuff != null) {
				if (payload.hit && payload.sound != null && payload.soundPos != null) {
					stand.clientStuff.barrageHitSounds.hit(payload.sound.value(), payload.soundPos);
				}
				else {
					stand.clientStuff.barrageHitSounds.hitMissed();
				}
			}
		}
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package com.github.standobyte.jojo.network.s2c;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrSetStandEntityPacket(int userId, int standEntityId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrSetStandEntityPacket> type;
	
	public static class Handler implements PacketsRegister.PacketCodecHandler<TrSetStandEntityPacket> {
		private static final int MAX_PENDING_TICKS = 100;
		private static final Map<Integer, PendingStandEntityLink> PENDING_LINKS = new HashMap<>();
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrSetStandEntityPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrSetStandEntityPacket> reader() {
			return STREAM_CODEC;
		}
		
		
		public static final StreamCodec<RegistryFriendlyByteBuf, TrSetStandEntityPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrSetStandEntityPacket::userId,
				ByteBufCodecs.INT, TrSetStandEntityPacket::standEntityId,
				TrSetStandEntityPacket::new);

		@Override
		public void handle(TrSetStandEntityPacket payload, IPayloadContext context) {
			if (!tryApply(payload, false)) {
				PENDING_LINKS.put(payload.userId, new PendingStandEntityLink(payload, 0));
				JojoMod.getLogger().warn("Stand entity link pending on client: userId={}, standEntityId={} (entity not available yet).",
						payload.userId, payload.standEntityId);
			}
		}

		public static void tickPendingLinks() {
			if (PENDING_LINKS.isEmpty()) {
				return;
			}
			Iterator<Map.Entry<Integer, PendingStandEntityLink>> iter = PENDING_LINKS.entrySet().iterator();
			while (iter.hasNext()) {
				PendingStandEntityLink pending = iter.next().getValue();
				if (tryApply(pending.payload, true)) {
					iter.remove();
				}
				else if (pending.ticks++ >= MAX_PENDING_TICKS) {
					iter.remove();
					JojoMod.getLogger().error("Stand entity link failed on client after {} ticks: userId={}, standEntityId={}.",
							MAX_PENDING_TICKS, pending.payload.userId, pending.payload.standEntityId);
				}
			}
		}

		private static boolean tryApply(TrSetStandEntityPacket payload, boolean retry) {
			Entity userEntity = ClientProxy.getEntityById(payload.userId);
			if (!(userEntity instanceof LivingEntity userLiving)) {
				return false;
			}
			StandPower standPower = StandPower.get(userLiving);
			if (standPower == null) {
				return false;
			}
			if (payload.standEntityId <= 0) {
				standPower.setSummonedStand(null);
				JojoMod.getLogger().info("Stand entity link cleared on client: userId={}.", payload.userId);
				return true;
			}
			Entity entity = ClientProxy.getEntityById(payload.standEntityId);
			if (entity instanceof StandEntity stand) {
				standPower.setSummonedStand(stand);
				JojoMod.getLogger().info("Stand entity link applied on client{}: userId={}, standEntityId={}, entityType={}, pos={}.",
						retry ? " after retry" : "", payload.userId, payload.standEntityId,
						stand.getType().builtInRegistryHolder().key().location(), stand.position());
				return true;
			}
			return false;
		}

		private static class PendingStandEntityLink {
			private final TrSetStandEntityPacket payload;
			private int ticks;

			private PendingStandEntityLink(TrSetStandEntityPacket payload, int ticks) {
				this.payload = payload;
				this.ticks = ticks;
			}
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

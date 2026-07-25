package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.v1_21_4_stuff.missingmethods._Vec3;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrDirectEntityPosPacket(int entityId, Vec3 pos) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrDirectEntityPosPacket> type;
	
	public static class Handler implements PacketsRegister.PacketCodecHandler<TrDirectEntityPosPacket> {
		
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrDirectEntityPosPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrDirectEntityPosPacket> reader() {
			return STREAM_CODEC;
		}
		
		public static final StreamCodec<RegistryFriendlyByteBuf, TrDirectEntityPosPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrDirectEntityPosPacket::entityId,
				_Vec3.STREAM_CODEC, TrDirectEntityPosPacket::pos,
				TrDirectEntityPosPacket::new);

		@Override
		public void handle(TrDirectEntityPosPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				Vec3 pos = payload.pos;
				entity.moveTo(pos.x, pos.y, pos.z);
				entity.setPos(pos.x, pos.y, pos.z);
				entity.xo = pos.x;
				entity.yo = pos.y;
				entity.zo = pos.z;
				entity.xOld = pos.x;
				entity.yOld = pos.y;
				entity.zOld = pos.z;
			}
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrRefreshMovementInTimeStopPacket(int entityId, int chunkX, int chunkZ, boolean canMove) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrRefreshMovementInTimeStopPacket> type;

	public TrRefreshMovementInTimeStopPacket(int entityId, ChunkPos chunkPos, boolean canMove) {
		this(entityId, chunkPos.x, chunkPos.z, canMove);
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrRefreshMovementInTimeStopPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrRefreshMovementInTimeStopPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrRefreshMovementInTimeStopPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrRefreshMovementInTimeStopPacket> STREAM_CODEC = StreamCodec.ofMember(
				TrRefreshMovementInTimeStopPacket::write,
				TrRefreshMovementInTimeStopPacket::new);

		@Override
		public void handle(TrRefreshMovementInTimeStopPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity != null) {
				ClientTimeStopHandler.refreshMovementInTimeStop(entity, new ChunkPos(payload.chunkX, payload.chunkZ), payload.canMove);
			}
		}
	}

	public TrRefreshMovementInTimeStopPacket(RegistryFriendlyByteBuf buf) {
		this(buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(entityId);
		buf.writeInt(chunkX);
		buf.writeInt(chunkZ);
		buf.writeBoolean(canMove);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

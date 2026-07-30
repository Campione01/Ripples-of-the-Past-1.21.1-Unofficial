package com.github.standobyte.jojo.network.s2c;

import java.util.Optional;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientTimeStopHandler;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrTimeStopInstancePacket(int id, int ticksLeft, int totalTicks, int chunkX, int chunkZ, int chunkRange, int userId, String visualRoute, Optional<ResourceLocation> standTypeId, Optional<ResourceLocation> selectedSkin, int resumeSoundUserId, int resumeVoiceLineUserId, boolean ticksManuallySet, boolean forceResumeVoiceLine, float staminaCostTick, int ticksPassed, boolean refundUnusedStartCost, boolean remove, boolean openingVisual) implements CustomPacketPayload {
	private static final int MAX_VISUAL_ROUTE_LENGTH = 128;
	private static CustomPacketPayload.Type<TrTimeStopInstancePacket> type;

	public TrTimeStopInstancePacket {
		visualRoute = NetworkPayloadValidation.requireUtfLength(
				visualRoute, MAX_VISUAL_ROUTE_LENGTH, "time-stop visual route");
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrTimeStopInstancePacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrTimeStopInstancePacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrTimeStopInstancePacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrTimeStopInstancePacket> STREAM_CODEC = StreamCodec.ofMember(
				TrTimeStopInstancePacket::write, TrTimeStopInstancePacket::new);

		@Override
		public void handle(TrTimeStopInstancePacket payload, IPayloadContext context) {
			if (payload.remove) {
				if (payload.id < 0) {
					TimeStopState.clearClientInstances();
				}
				else {
					TimeStopState.removeClientInstance(payload.id);
				}
				ClientTimeStopHandler.updateTimeStopTicksLeft();
				return;
			}
			TimeStopState.Instance instance = new TimeStopState.Instance(
					payload.id,
					payload.ticksLeft,
					payload.totalTicks,
					new ChunkPos(payload.chunkX, payload.chunkZ),
					payload.chunkRange,
					payload.userId,
					payload.visualRoute,
					payload.standTypeId,
					payload.selectedSkin,
					payload.resumeSoundUserId,
					payload.resumeVoiceLineUserId,
					payload.ticksManuallySet,
					payload.forceResumeVoiceLine,
					payload.staminaCostTick,
					payload.ticksPassed,
					payload.refundUnusedStartCost);
			TimeStopState.putClientInstance(instance);
			ClientTimeStopHandler.updateTimeStopTicksLeft();
			if (payload.openingVisual) {
				ModShaders shaders = ModShaders.getInstance();
				if (shaders != null && shaders.timeStopShaderManager != null) {
					shaders.timeStopShaderManager.setTimeStopVisuals(instance);
				}
			}
		}
	}

	public TrTimeStopInstancePacket(RegistryFriendlyByteBuf buf) {
		this(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf(MAX_VISUAL_ROUTE_LENGTH), NetworkUtil.readOptional(buf, ResourceLocation.STREAM_CODEC), NetworkUtil.readOptional(buf, ResourceLocation.STREAM_CODEC), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readFloat(), buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(id);
		buf.writeInt(ticksLeft);
		buf.writeInt(totalTicks);
		buf.writeInt(chunkX);
		buf.writeInt(chunkZ);
		buf.writeInt(chunkRange);
		buf.writeInt(userId);
		buf.writeUtf(visualRoute, MAX_VISUAL_ROUTE_LENGTH);
		NetworkUtil.writeOptional(standTypeId, buf, ResourceLocation.STREAM_CODEC);
		NetworkUtil.writeOptional(selectedSkin, buf, ResourceLocation.STREAM_CODEC);
		buf.writeInt(resumeSoundUserId);
		buf.writeInt(resumeVoiceLineUserId);
		buf.writeBoolean(ticksManuallySet);
		buf.writeBoolean(forceResumeVoiceLine);
		buf.writeFloat(staminaCostTick);
		buf.writeInt(ticksPassed);
		buf.writeBoolean(refundUnusedStartCost);
		buf.writeBoolean(remove);
		buf.writeBoolean(openingVisual);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

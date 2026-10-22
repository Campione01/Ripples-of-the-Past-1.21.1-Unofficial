package rotp.core.powersystem.entityaction.netcode;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.functions_network.NetworkUtil;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The targets the server changed while an action runs: the target it keeps, the one its Stand turns to, how the Stand
 * aims, and its task's target, which turns its offsets. 1.16 StandEntity.setTaskTarget sent the new task target to
 * tracking clients (TrStandTaskTargetPacket), and clients never judged it themselves (StandEntityTask.keepTarget passed
 * everything on a client).
 */
public record TrEntityActionTargetPacket(int performerId,
		UUID performerUuid,
		long actionGeneration,
		int actionId,
		@Nullable ActionTarget keptTarget,
		@Nullable ActionTarget rotationTarget,
		AimingEntity aimAs,
		@Nullable ActionTarget taskTarget,
		int slot) implements CustomPacketPayload {

	public TrEntityActionTargetPacket {
		Objects.requireNonNull(performerUuid, "performerUuid");
		Objects.requireNonNull(aimAs, "aimAs");
		NetworkPayloadValidation.requireOutboundGeneration(
				actionGeneration, "entity action target");
	}

	public TrEntityActionTargetPacket(int performerId, UUID performerUuid, long actionGeneration, int actionId,
			@Nullable ActionTarget keptTarget, @Nullable ActionTarget rotationTarget, AimingEntity aimAs,
			@Nullable ActionTarget taskTarget) {
		this(performerId, performerUuid, actionGeneration, actionId, keptTarget, rotationTarget, aimAs, taskTarget, 0);
	}

	private TrEntityActionTargetPacket(RegistryFriendlyByteBuf buf) {
		this(
				buf.readInt(),
				buf.readUUID(),
				buf.readVarLong(),
				buf.readVarInt(),
				NetworkUtil.readOptional(buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null),
				NetworkUtil.readOptional(buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null),
				buf.readEnum(AimingEntity.class),
				NetworkUtil.readOptional(buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID).orElse(null),
				buf.readUnsignedByte());
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(performerId);
		buf.writeUUID(performerUuid);
		buf.writeVarLong(actionGeneration);
		buf.writeVarInt(actionId);
		NetworkUtil.writeOptionally(keptTarget, buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
		NetworkUtil.writeOptionally(rotationTarget, buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
		buf.writeEnum(aimAs);
		NetworkUtil.writeOptionally(taskTarget, buf, ActionTarget.STREAM_CODEC_UNRESOLVED_ENTITY_ID);
		buf.writeByte(slot);
	}

	private static CustomPacketPayload.Type<TrEntityActionTargetPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrEntityActionTargetPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrEntityActionTargetPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrEntityActionTargetPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf,
				TrEntityActionTargetPacket> STREAM_CODEC =
				StreamCodec.ofMember(
						TrEntityActionTargetPacket::write,
						TrEntityActionTargetPacket::new);

		@Override
		public void handle(TrEntityActionTargetPacket payload, IPayloadContext context) {
			ClientEntityActionSyncQueue.applyOrQueueTarget(
					context.listener(), payload);
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

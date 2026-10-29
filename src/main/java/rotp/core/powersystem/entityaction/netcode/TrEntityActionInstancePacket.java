package rotp.core.powersystem.entityaction.netcode;

import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.network.NetworkPayloadValidation;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class TrEntityActionInstancePacket implements CustomPacketPayload {
	private final int performerId;
	private final UUID performerUuid;
	private final long generation;
	private final int slot;
	// the action encoded as it was when the packet was made, or as received: Netty writes the packet later, on its own
	// thread, while the server thread goes on changing the action (1.16 synced a value taken at send time)
	@Nullable private final byte[] actionData;
	
	public TrEntityActionInstancePacket(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable EntityActionInstance action) {
		this(performerId, performerUuid, generation, action, 0);
	}

	/** slot: the performer's action slot, LivingComponentAction#slotId (the user's Stand-power slot is 1). */
	public TrEntityActionInstancePacket(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable EntityActionInstance action,
			int slot) {
		this(performerId, performerUuid, generation, encodeAction(action), slot);
	}

	private TrEntityActionInstancePacket(
			int performerId,
			UUID performerUuid,
			long generation,
			@Nullable byte[] actionData,
			int slot) {
		this.performerId = performerId;
		this.performerUuid = Objects.requireNonNull(
				performerUuid, "performerUuid");
		this.generation = generation;
		this.slot = slot;
		this.actionData = actionData;
	}

	private static byte[] encodeAction(@Nullable EntityActionInstance action) {
		FriendlyByteBuf actionData = new FriendlyByteBuf(
				Unpooled.buffer(
						256,
						NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES));
		try {
			EntityActionInstance.encode(actionData, action);
			int length = NetworkPayloadValidation.requireOutboundByteLength(
					actionData.readableBytes(),
					NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
					"entity action");
			byte[] bytes = new byte[length];
			actionData.readBytes(bytes);
			return bytes;
		}
		finally {
			actionData.release();
		}
	}
	
	
	
	private static CustomPacketPayload.Type<TrEntityActionInstancePacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<TrEntityActionInstancePacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrEntityActionInstancePacket> type() {
			return type;
		}

		@Override
		public void encode(TrEntityActionInstancePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.performerId);
			buf.writeUUID(packet.performerUuid);
			buf.writeVarLong(NetworkPayloadValidation.requireOutboundGeneration(
					packet.generation, "entity action"));
			buf.writeByte(packet.slot);
			buf.writeBytes(packet.actionData);
		}

		@Override
		public TrEntityActionInstancePacket decode(RegistryFriendlyByteBuf buf) {
			int performerId = buf.readInt();
			UUID performerUuid = buf.readUUID();
			long generation = NetworkPayloadValidation.requireGeneration(
					buf.readVarLong(), "entity action");
			int slot = buf.readUnsignedByte();
			return new TrEntityActionInstancePacket(
					performerId, performerUuid, generation,
					NetworkUtil.extraPacketDataBytes(
							buf,
							NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
							"entity action"),
					slot);
		}
		
		@Override
		public void handle(TrEntityActionInstancePacket payload, IPayloadContext context) {
			ClientEntityActionSyncQueue.applyOrQueueAction(
					context.listener(),
					payload.performerId,
					payload.performerUuid,
					payload.generation,
					payload.actionData,
					payload.slot);
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

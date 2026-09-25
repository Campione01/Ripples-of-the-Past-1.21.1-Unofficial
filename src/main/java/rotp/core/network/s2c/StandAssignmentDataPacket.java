package rotp.core.network.s2c;

import java.util.List;
import java.util.Optional;

import rotp.core.PacketsRegister;
import rotp.core.command.configpack.PlayerStandAssignmentConfig;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 StandAssignmentDataPacket: the local player's /jojoconfig assign_stand entry,
 * so the Stand Arrow tooltip can strike through Stands the player can't get. Empty = unrestricted.
 */
public record StandAssignmentDataPacket(Optional<List<ResourceLocation>> stands) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<StandAssignmentDataPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<StandAssignmentDataPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<StandAssignmentDataPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, StandAssignmentDataPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, StandAssignmentDataPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())),
				StandAssignmentDataPacket::stands,
				StandAssignmentDataPacket::new);

		@Override
		public void handle(StandAssignmentDataPacket payload, IPayloadContext context) {
			PlayerStandAssignmentConfig.handleClientPacket(payload.stands());
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

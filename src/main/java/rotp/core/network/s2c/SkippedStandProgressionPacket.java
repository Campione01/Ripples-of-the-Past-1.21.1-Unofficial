package rotp.core.network.s2c;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.client.ClientPowerCache;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkippedStandProgressionPacket(boolean skipped) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<SkippedStandProgressionPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<SkippedStandProgressionPacket> {
		public static final StreamCodec<ByteBuf, SkippedStandProgressionPacket> STREAM_CODEC =
				ByteBufCodecs.BOOL.map(SkippedStandProgressionPacket::new, SkippedStandProgressionPacket::skipped);

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<SkippedStandProgressionPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, SkippedStandProgressionPacket> reader() {
			return STREAM_CODEC;
		}

		@Override
		public void handle(SkippedStandProgressionPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				StandPower power = PowerClass.STAND.attachGet(player);
				power.applySyncedProgressionSkipped(payload.skipped);
				ClientPowerCache.refreshPower(power);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

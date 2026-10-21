package rotp.core.network.s2c;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandAwakening.AwakeningStage;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A Stand user's awakening stage for the players tracking it, whose StandPower.usesResolve reads it. */
public record TrStandAwakeningStagePacket(int userId, AwakeningStage stage) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrStandAwakeningStagePacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrStandAwakeningStagePacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrStandAwakeningStagePacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrStandAwakeningStagePacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrStandAwakeningStagePacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrStandAwakeningStagePacket::userId,
				NeoForgeStreamCodecs.enumCodec(AwakeningStage.class), TrStandAwakeningStagePacket::stage,
				TrStandAwakeningStagePacket::new);

		@Override
		public void handle(TrStandAwakeningStagePacket payload, IPayloadContext context) {
			payload.applyTo(ClientProxy.getEntityById(payload.userId));
		}
	}

	public void applyTo(@Nullable Entity userEntity) {
		if (userEntity instanceof LivingEntity user) {
			// attached, as the Stand instance sync does: a mob's client copy may have no Stand data yet
			StandPower standPower = PowerClass.STAND.attachGet(user);
			standPower.userStandAwakeningState.stage = stage;
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

package com.github.standobyte.jojo.subsystems.entity_possessionv2;

import java.util.Optional;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrPossessEntityPacket(int possessingId, int targetId, @Nullable String possessionType,
		Optional<GameType> prePossessGameMode) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrPossessEntityPacket> type;

	public TrPossessEntityPacket {
		if (prePossessGameMode == null) {
			prePossessGameMode = Optional.empty();
		}
	}
	
	public static class Handler implements PacketsRegister.PacketCodecHandler<TrPossessEntityPacket> {

		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPossessEntityPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrPossessEntityPacket> reader() {
			return STREAM_CODEC;
		}


		private static final StreamCodec<RegistryFriendlyByteBuf, Optional<GameType>> GAME_TYPE_OPTIONAL_CODEC = new StreamCodec<>() {
			@Override
			public void encode(RegistryFriendlyByteBuf buffer, Optional<GameType> value) {
				buffer.writeBoolean(value.isPresent());
				value.ifPresent(buffer::writeEnum);
			}

			@Override
			public Optional<GameType> decode(RegistryFriendlyByteBuf buffer) {
				return buffer.readBoolean() ? Optional.of(buffer.readEnum(GameType.class)) : Optional.empty();
			}
		};

		public static final StreamCodec<RegistryFriendlyByteBuf, TrPossessEntityPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrPossessEntityPacket::possessingId,
				ByteBufCodecs.INT, TrPossessEntityPacket::targetId,
				ByteBufCodecs.STRING_UTF8.apply(NetworkUtil::nullableCodec), TrPossessEntityPacket::possessionType,
				GAME_TYPE_OPTIONAL_CODEC, TrPossessEntityPacket::prePossessGameMode,
				TrPossessEntityPacket::new);

		@Override
		public void handle(TrPossessEntityPacket packet, IPayloadContext context) {
			Entity possessing = ClientProxy.getEntityById(packet.possessingId);
			if (possessing instanceof LivingEntity living) {
				LivingComponentPossession.setPossessionTargetFromPacket(living, ClientProxy.getEntityById(packet.targetId),
						packet.possessionType, packet.prePossessGameMode);
			}
		}

	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}

}

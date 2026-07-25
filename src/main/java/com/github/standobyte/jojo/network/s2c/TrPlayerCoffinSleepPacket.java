package com.github.standobyte.jojo.network.s2c;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.mechanics.coffin.PlayerCoffinSleepData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrPlayerCoffinSleepPacket(boolean isRespawning) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrPlayerCoffinSleepPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrPlayerCoffinSleepPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPlayerCoffinSleepPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrPlayerCoffinSleepPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrPlayerCoffinSleepPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, TrPlayerCoffinSleepPacket::isRespawning,
				TrPlayerCoffinSleepPacket::new);

		@Override
		public void handle(TrPlayerCoffinSleepPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				PlayerCoffinSleepData.get(player).setFromPacket(payload.isRespawning());
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

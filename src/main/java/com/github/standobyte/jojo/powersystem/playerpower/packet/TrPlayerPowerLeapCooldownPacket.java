package com.github.standobyte.jojo.powersystem.playerpower.packet;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrPlayerPowerLeapCooldownPacket(int entityId, int cooldown) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrPlayerPowerLeapCooldownPacket> type;

	public static class Handler implements PacketsRegister.PacketCodecHandler<TrPlayerPowerLeapCooldownPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrPlayerPowerLeapCooldownPacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, TrPlayerPowerLeapCooldownPacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, TrPlayerPowerLeapCooldownPacket> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.INT, TrPlayerPowerLeapCooldownPacket::entityId,
				ByteBufCodecs.INT, TrPlayerPowerLeapCooldownPacket::cooldown,
				TrPlayerPowerLeapCooldownPacket::new);

		@Override
		public void handle(TrPlayerPowerLeapCooldownPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PlayerPower playerPower = PlayerPower.get(living);
				if (playerPower != null) {
					playerPower.setLeapCooldown(payload.cooldown);
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

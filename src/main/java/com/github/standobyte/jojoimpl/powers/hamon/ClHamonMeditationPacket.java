package com.github.standobyte.jojoimpl.powers.hamon;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonMeditationPacket(@Nullable Boolean value) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonMeditationPacket> packetType;

	public ClHamonMeditationPacket() {
		this(null);
	}

	public ClHamonMeditationPacket(boolean value) {
		this(Boolean.valueOf(value));
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonMeditationPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonMeditationPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonMeditationPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.value != null);
			if (packet.value != null) {
				buf.writeBoolean(packet.value);
			}
		}

		@Override
		public ClHamonMeditationPacket decode(RegistryFriendlyByteBuf buf) {
			boolean hasValue = buf.readBoolean();
			return hasValue ? new ClHamonMeditationPacket(buf.readBoolean()) : new ClHamonMeditationPacket();
		}

		@Override
		public void handle(ClHamonMeditationPacket payload, IPayloadContext context) {
			Player player = context.player();
			PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				if (player.onGround() || hamon.isMeditating()) {
					hamon.setIsMeditating(player, payload.value != null ? payload.value : !hamon.isMeditating());
				}
			});
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.Set;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonWindowOpenedPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonWindowOpenedPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonWindowOpenedPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonWindowOpenedPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonWindowOpenedPacket packet, RegistryFriendlyByteBuf buf) {}

		@Override
		public ClHamonWindowOpenedPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonWindowOpenedPacket();
		}

		@Override
		public void handle(ClHamonWindowOpenedPacket payload, IPayloadContext context) {
			if (context.player() instanceof ServerPlayer player) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					PacketDistributor.sendToPlayer(player, HamonExercisesPacket.allData(hamon));
					Set<String> skills = HamonUtil.nearbyTeachersSkills(player);
					PacketDistributor.sendToPlayer(player, skills == null
							? new HamonTeachersSkillsPacket()
							: new HamonTeachersSkillsPacket(skills));
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

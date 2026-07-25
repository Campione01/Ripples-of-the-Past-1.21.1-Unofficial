package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonDoubleShiftPressPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonDoubleShiftPressPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonDoubleShiftPressPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonDoubleShiftPressPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonDoubleShiftPressPacket packet, RegistryFriendlyByteBuf buf) {}

		@Override
		public ClHamonDoubleShiftPressPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonDoubleShiftPressPacket();
		}

		@Override
		public void handle(ClHamonDoubleShiftPressPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (player.isAlive() && player.onGround()) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					if (hamon.isSkillLearned(ModHamonSkills.LIQUID_WALKING.get())) {
						hamon.setDoubleShiftPress(player);
					}
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

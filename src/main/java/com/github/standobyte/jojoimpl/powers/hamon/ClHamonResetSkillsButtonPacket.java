package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonResetSkillsButtonPacket(HamonSkillsTab skillsTab) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonResetSkillsButtonPacket> packetType;

	public static ClHamonResetSkillsButtonPacket resetTab(HamonSkillsTab type) {
		return new ClHamonResetSkillsButtonPacket(type);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonResetSkillsButtonPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonResetSkillsButtonPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonResetSkillsButtonPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeEnum(packet.skillsTab);
		}

		@Override
		public ClHamonResetSkillsButtonPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonResetSkillsButtonPacket(buf.readEnum(HamonSkillsTab.class));
		}

		@Override
		public void handle(ClHamonResetSkillsButtonPacket payload, IPayloadContext context) {
			Player player = context.player();
			PlayerPower.getPowerData(player, HamonPowerType.HAMON).ifPresent(hamon -> {
				hamon.resetHamonSkills(player, payload.skillsTab);
			});
		}
	}

	public static enum HamonSkillsTab {
		STRENGTH,
		CONTROL,
		TECHNIQUE
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

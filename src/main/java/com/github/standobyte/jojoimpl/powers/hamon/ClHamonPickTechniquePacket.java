package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonPickTechniquePacket(ResourceLocation techniqueId) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonPickTechniquePacket> type;

	public static ClHamonPickTechniquePacket pickTechnique(String techniqueName) {
		return pickTechnique(JojoMod.resLoc(techniqueName));
	}

	public static ClHamonPickTechniquePacket pickTechnique(ResourceLocation techniqueId) {
		return new ClHamonPickTechniquePacket(techniqueId);
	}

	public static class Handler implements PacketsRegister.PacketCodecHandler<ClHamonPickTechniquePacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonPickTechniquePacket> type() {
			return type;
		}

		@Override
		public StreamCodec<? super RegistryFriendlyByteBuf, ClHamonPickTechniquePacket> reader() {
			return STREAM_CODEC;
		}

		public static final StreamCodec<RegistryFriendlyByteBuf, ClHamonPickTechniquePacket> STREAM_CODEC = StreamCodec.composite(
				ResourceLocation.STREAM_CODEC, ClHamonPickTechniquePacket::techniqueId,
				ClHamonPickTechniquePacket::new);

		@Override
		public void handle(ClHamonPickTechniquePacket payload, IPayloadContext context) {
			Player player = context.player();
			PlayerPower.getPowerData(player, HamonPowerType.HAMON).ifPresent(hamon -> {
				HamonTechnique technique = ModHamonSkills.techniqueByName(payload.techniqueId.getPath());
				if (technique != null && technique.getRegistryKey().equals(payload.techniqueId)) {
					hamon.pickHamonTechnique(player, technique);
				}
			});
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

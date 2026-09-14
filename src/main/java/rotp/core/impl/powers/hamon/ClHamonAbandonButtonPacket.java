package rotp.core.impl.powers.hamon;

import rotp.core.PacketsRegister;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonAbandonButtonPacket() implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClHamonAbandonButtonPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonAbandonButtonPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonAbandonButtonPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonAbandonButtonPacket packet, RegistryFriendlyByteBuf buf) {}

		@Override
		public ClHamonAbandonButtonPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonAbandonButtonPacket();
		}

		@Override
		public void handle(ClHamonAbandonButtonPacket payload, IPayloadContext context) {
			PlayerPower power = PlayerPower.get(context.player());
			if (power != null && power.getPowerType() == ModPlayerPowers.HAMON.get()) {
				power.setPowerType(null);
				if (context.player() instanceof ServerPlayer player) {
					ModCriteriaTriggers.triggerAbandonHamon(player);
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

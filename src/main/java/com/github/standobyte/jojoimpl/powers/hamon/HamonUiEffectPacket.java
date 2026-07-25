package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ui.hud_power.PowerHud;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HamonUiEffectPacket(Type effectType) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<HamonUiEffectPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<HamonUiEffectPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public CustomPacketPayload.Type<HamonUiEffectPacket> type() {
			return packetType;
		}

		@Override
		public void encode(HamonUiEffectPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeEnum(packet.effectType);
		}

		@Override
		public HamonUiEffectPacket decode(RegistryFriendlyByteBuf buf) {
			return new HamonUiEffectPacket(buf.readEnum(Type.class));
		}

		@Override
		public void handle(HamonUiEffectPacket payload, IPayloadContext context) {
			switch (payload.effectType) {
			case NO_ENERGY -> PowerHud.triggerHamonNoEnergyFeedback();
			case OUT_OF_BREATH -> PowerHud.setHamonOutOfBreath(false);
			case OUT_OF_BREATH_MASK -> PowerHud.setHamonOutOfBreath(true);
			}
		}
	}

	public enum Type {
		OUT_OF_BREATH,
		OUT_OF_BREATH_MASK,
		NO_ENERGY
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

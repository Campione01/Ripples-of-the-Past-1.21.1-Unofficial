package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonMeditationPacket(int entityId, boolean meditation) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonMeditationPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonMeditationPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonMeditationPacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonMeditationPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeBoolean(packet.meditation);
		}

		@Override
		public TrHamonMeditationPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrHamonMeditationPacket(buf.readInt(), buf.readBoolean());
		}

		@Override
		public void handle(TrHamonMeditationPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.setIsMeditating(living, payload.meditation);
					Minecraft minecraft = Minecraft.getInstance();
					if (payload.meditation && living == minecraft.player
							&& minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
						minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
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

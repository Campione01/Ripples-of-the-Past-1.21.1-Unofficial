package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrHamonSyncPlayerLearnerPacket(int teacherId, int learnerId, boolean playerWantsToLearn) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<TrHamonSyncPlayerLearnerPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrHamonSyncPlayerLearnerPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrHamonSyncPlayerLearnerPacket> type() {
			return packetType;
		}

		@Override
		public void encode(TrHamonSyncPlayerLearnerPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.teacherId);
			buf.writeInt(packet.learnerId);
			buf.writeBoolean(packet.playerWantsToLearn);
		}

		@Override
		public TrHamonSyncPlayerLearnerPacket decode(RegistryFriendlyByteBuf buf) {
			return new TrHamonSyncPlayerLearnerPacket(buf.readInt(), buf.readInt(), buf.readBoolean());
		}

		@Override
		public void handle(TrHamonSyncPlayerLearnerPacket payload, IPayloadContext context) {
			Entity teacherEntity = ClientProxy.getEntityById(payload.teacherId);
			Entity learnerEntity = ClientProxy.getEntityById(payload.learnerId);
			if (teacherEntity instanceof LivingEntity teacher && learnerEntity instanceof Player learner) {
				PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					if (hamon.playerWantsToLearn(learner) != payload.playerWantsToLearn) {
						hamon.setNewPlayerLearner(learner, payload.playerWantsToLearn);
					}
					Entity clientPlayer = ClientProxy.getClientPlayer();
					if (payload.playerWantsToLearn) {
						if (clientPlayer == teacher) {
							ClientProxy.setOverlayMessage(Component.translatable("jojo.chat.message.new_hamon_learner",
									learner.getDisplayName(), Component.keybind("jojo_ripples.key.jojo_menu")), false);
						}
						else if (clientPlayer == learner) {
							ClientProxy.setOverlayMessage(Component.translatable("jojo.chat.message.asked_hamon_teacher",
									teacher.getDisplayName()), false);
						}
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

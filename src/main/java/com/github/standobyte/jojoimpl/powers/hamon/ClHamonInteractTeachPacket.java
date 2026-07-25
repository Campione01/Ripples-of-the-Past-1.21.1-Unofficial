package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonInteractTeachPacket(int entityId) implements CustomPacketPayload {
	private static final double MAX_INTERACTION_DISTANCE_SQR = 16.0D;
	private static CustomPacketPayload.Type<ClHamonInteractTeachPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonInteractTeachPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonInteractTeachPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonInteractTeachPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
		}

		@Override
		public ClHamonInteractTeachPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonInteractTeachPacket(buf.readInt());
		}

		@Override
		public void handle(ClHamonInteractTeachPacket payload, IPayloadContext context) {
			Player teacher = context.player();
			if (!teacher.isAlive() || teacher.isSpectator()) {
				return;
			}
			Entity targetEntity = teacher.level().getEntity(payload.entityId);
			if (!(targetEntity instanceof Player learner) || learner == teacher
					|| !learner.isAlive() || learner.isSpectator()
					|| teacher.distanceToSqr(learner) > MAX_INTERACTION_DISTANCE_SQR
					|| !teacher.hasLineOfSight(learner)) {
				return;
			}
			PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				if (hamon.playerWantsToLearn(learner)) {
					hamon.interactWithNewLearner(teacher, learner);
				}
			});
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

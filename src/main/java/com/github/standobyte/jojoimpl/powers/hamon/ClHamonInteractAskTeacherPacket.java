package com.github.standobyte.jojoimpl.powers.hamon;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClHamonInteractAskTeacherPacket(int entityId) implements CustomPacketPayload {
	private static final double MAX_INTERACTION_DISTANCE_SQR = 16.0D;
	private static CustomPacketPayload.Type<ClHamonInteractAskTeacherPacket> packetType;

	public static class Handler implements PacketsRegister.PacketOGHandler<ClHamonInteractAskTeacherPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClHamonInteractAskTeacherPacket> type() {
			return packetType;
		}

		@Override
		public void encode(ClHamonInteractAskTeacherPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
		}

		@Override
		public ClHamonInteractAskTeacherPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClHamonInteractAskTeacherPacket(buf.readInt());
		}

		@Override
		public void handle(ClHamonInteractAskTeacherPacket payload, IPayloadContext context) {
			Player player = context.player();
			if (!player.isAlive() || player.isSpectator()) {
				return;
			}
			Entity targetEntity = player.level().getEntity(payload.entityId);
			if (!(targetEntity instanceof LivingEntity teacher) || teacher == player
					|| !teacher.isAlive() || teacher.isSpectator()
					|| player.distanceToSqr(teacher) > MAX_INTERACTION_DISTANCE_SQR
					|| !player.hasLineOfSight(teacher)) {
				return;
			}
			PlayerPower playerPower = PlayerPower.get(player);
			if (playerPower == null || playerPower.hasPower()) {
				return;
			}
			PlayerPower.getPowerData(teacher, ModPlayerPowers.HAMON).ifPresent(teacherHamon ->
					HamonUtil.interactWithHamonTeacher(player.level(), player, teacher, teacherHamon));
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.Arrays;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData.Exercise;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonTrainingHudFeedback;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HamonExercisesPacket(int[] exerciseTicks, boolean sendBonus, float trainingBonus,
		int canSkipTrainingDays, int completedExerciseFeedback, String breathingIncreaseFeedback) implements CustomPacketPayload {
	private static final int MAX_FEEDBACK_LENGTH = 128;
	private static CustomPacketPayload.Type<HamonExercisesPacket> packetType;

	public HamonExercisesPacket {
		if (exerciseTicks.length != Exercise.values().length) {
			throw new IllegalArgumentException("Invalid outbound Hamon exercise count "
					+ exerciseTicks.length + " (expected " + Exercise.values().length + ")");
		}
		exerciseTicks = exerciseTicks.clone();
		breathingIncreaseFeedback = NetworkPayloadValidation.requireUtfLength(
				breathingIncreaseFeedback, MAX_FEEDBACK_LENGTH,
				"Hamon breathing feedback");
	}

	public static HamonExercisesPacket allData(HamonData hamon) {
		return new HamonExercisesPacket(
				Arrays.stream(Exercise.values()).mapToInt(hamon::getExerciseTicks).toArray(),
				true,
				hamon.getTrainingBonus(false),
				hamon.getCanSkipTrainingDays(),
				0, "");
	}

	public static HamonExercisesPacket exercisesOnly(HamonData hamon) {
		return new HamonExercisesPacket(
				Arrays.stream(Exercise.values()).mapToInt(hamon::getExerciseTicks).toArray(),
				false, 0.0F, 0, 0, "");
	}

	public static HamonExercisesPacket exerciseCompleted(HamonData hamon, int completedExercises, String breathingIncrease) {
		return new HamonExercisesPacket(
				Arrays.stream(Exercise.values()).mapToInt(hamon::getExerciseTicks).toArray(),
				false, 0.0F, 0, completedExercises, breathingIncrease);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<HamonExercisesPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<HamonExercisesPacket> type() {
			return packetType;
		}

		@Override
		public void encode(HamonExercisesPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(packet.exerciseTicks.length);
			for (int tick : packet.exerciseTicks) {
				buf.writeVarInt(tick);
			}
			buf.writeBoolean(packet.sendBonus);
			if (packet.sendBonus) {
				buf.writeFloat(packet.trainingBonus);
				buf.writeVarInt(packet.canSkipTrainingDays);
			}
			buf.writeVarInt(packet.completedExerciseFeedback);
			if (packet.completedExerciseFeedback == 3) {
				buf.writeUtf(packet.breathingIncreaseFeedback, MAX_FEEDBACK_LENGTH);
			}
		}

		@Override
		public HamonExercisesPacket decode(RegistryFriendlyByteBuf buf) {
			int exerciseCount = buf.readVarInt();
			if (exerciseCount != Exercise.values().length) {
				throw new DecoderException("Invalid Hamon exercise count: " + exerciseCount
						+ " (expected " + Exercise.values().length + ")");
			}
			int[] exerciseTicks = new int[exerciseCount];
			for (int i = 0; i < exerciseTicks.length; i++) {
				exerciseTicks[i] = buf.readVarInt();
			}
			boolean sendBonus = buf.readBoolean();
			float trainingBonus = sendBonus ? buf.readFloat() : 0.0F;
			int canSkipTrainingDays = sendBonus ? buf.readVarInt() : 0;
			int completedExerciseFeedback = buf.readVarInt();
			String breathingIncreaseFeedback = completedExerciseFeedback == 3
					? buf.readUtf(MAX_FEEDBACK_LENGTH)
					: "";
			return new HamonExercisesPacket(exerciseTicks, sendBonus, trainingBonus, canSkipTrainingDays,
					completedExerciseFeedback, breathingIncreaseFeedback);
		}

		@Override
		public void handle(HamonExercisesPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.setExerciseTicks(payload.exerciseTicks, true);
					if (payload.sendBonus) {
						hamon.setTrainingBonus(payload.trainingBonus);
						hamon.setCanSkipTrainingDays(payload.canSkipTrainingDays);
					}
					if (payload.completedExerciseFeedback > 0) {
						Component firstLine = Component.translatable(
								"hamon.exercise.all.count.message"
										+ (payload.completedExerciseFeedback >= HamonData.MAX_EXERCISES_NEEDED ? ".4" : ""),
								payload.completedExerciseFeedback, HamonData.MAX_EXERCISES_NEEDED);
						if (payload.completedExerciseFeedback == 3) {
							HamonTrainingHudFeedback.showExerciseCompletionOverlay(firstLine, Component.translatable(
									"hamon.exercise.all.count.message2.3", payload.breathingIncreaseFeedback));
						}
						else if (payload.completedExerciseFeedback >= HamonData.MAX_EXERCISES_NEEDED) {
							HamonTrainingHudFeedback.showExerciseCompletionOverlay(firstLine, Component.translatable(
									"hamon.exercise.all.count.message2.4", HamonData.CAN_SKIP_DAYS));
						}
						else {
							ClientProxy.setOverlayMessage(firstLine, false);
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

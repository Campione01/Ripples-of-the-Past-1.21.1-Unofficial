package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonSkillToast;
import com.github.standobyte.jojoimpl.powers.hamon.client.HamonTrainingHudFeedback;

import io.netty.handler.codec.DecoderException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HamonStatFeedbackPacket(int entityId, Stat stat, int statPoints, float breathingLevel,
		boolean showStatIncrease, List<String> newlyLearnableSkills) implements CustomPacketPayload {
	private static final int MAX_NEWLY_LEARNABLE_SKILLS = ModHamonSkills.SKILL_DEFINITIONS.size();
	private static final int MAX_SKILL_NAME_LENGTH = ModHamonSkills.SKILL_DEFINITIONS.stream()
			.mapToInt(definition -> definition.name().length())
			.max()
			.orElse(0);
	private static CustomPacketPayload.Type<HamonStatFeedbackPacket> packetType;

	public HamonStatFeedbackPacket {
		newlyLearnableSkills = List.copyOf(newlyLearnableSkills);
	}

	public static HamonStatFeedbackPacket stat(int entityId, HamonData.HamonStat stat, int points,
			boolean showStatIncrease, List<String> newlyLearnableSkills) {
		return new HamonStatFeedbackPacket(entityId,
				stat == HamonData.HamonStat.STRENGTH ? Stat.STRENGTH : Stat.CONTROL,
				points, 0.0F, showStatIncrease, newlyLearnableSkills);
	}

	public static HamonStatFeedbackPacket breathing(int entityId, float level, boolean showStatIncrease) {
		return new HamonStatFeedbackPacket(entityId, Stat.BREATHING, 0, level,
				showStatIncrease, List.of());
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<HamonStatFeedbackPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<HamonStatFeedbackPacket> type() {
			return packetType;
		}

		@Override
		public void encode(HamonStatFeedbackPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeInt(packet.entityId);
			buf.writeEnum(packet.stat);
			buf.writeBoolean(packet.showStatIncrease);
			switch (packet.stat) {
			case STRENGTH, CONTROL -> buf.writeInt(packet.statPoints);
			case BREATHING -> buf.writeFloat(packet.breathingLevel);
			}
			buf.writeVarInt(packet.newlyLearnableSkills.size());
			for (String skillName : packet.newlyLearnableSkills) {
				buf.writeUtf(skillName);
			}
		}

		@Override
		public HamonStatFeedbackPacket decode(RegistryFriendlyByteBuf buf) {
			int entityId = buf.readInt();
			Stat stat = buf.readEnum(Stat.class);
			boolean showStatIncrease = buf.readBoolean();
			int statPoints = stat == Stat.BREATHING ? 0 : buf.readInt();
			float breathingLevel = stat == Stat.BREATHING ? buf.readFloat() : 0.0F;
			int skillCount = buf.readVarInt();
			if (skillCount < 0 || skillCount > MAX_NEWLY_LEARNABLE_SKILLS) {
				throw new DecoderException("Invalid Hamon learnable skill count: " + skillCount
						+ " (max " + MAX_NEWLY_LEARNABLE_SKILLS + ")");
			}
			List<String> newlyLearnableSkills = new ArrayList<>(skillCount);
			for (int i = 0; i < skillCount; i++) {
				newlyLearnableSkills.add(buf.readUtf(MAX_SKILL_NAME_LENGTH));
			}
			return new HamonStatFeedbackPacket(entityId, stat, statPoints, breathingLevel,
					showStatIncrease, newlyLearnableSkills);
		}

		@Override
		public void handle(HamonStatFeedbackPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (!(entity instanceof LivingEntity living)) {
				return;
			}
			PlayerPower.getPowerData(living, ModPlayerPowers.HAMON).ifPresent(hamon -> {
				switch (payload.stat) {
				case STRENGTH -> hamon.applyStatPointsFromServer(HamonData.HamonStat.STRENGTH, payload.statPoints);
				case CONTROL -> hamon.applyStatPointsFromServer(HamonData.HamonStat.CONTROL, payload.statPoints);
				case BREATHING -> hamon.applyBreathingLevelFromServer(payload.breathingLevel);
				}

				if (living == ClientProxy.getClientPlayer()) {
					if (payload.showStatIncrease) {
						HamonTrainingHudFeedback.onStatIncreased(payload.stat);
					}
					for (String skillName : payload.newlyLearnableSkills) {
						HamonSkillDefinition skill = ModHamonSkills.definitionFor(skillName);
						if (skill != null) {
							HamonSkillToast.addOrUpdate(Minecraft.getInstance().getToasts(),
									HamonSkillToast.Type.forSkill(skill), skillName);
						}
					}
				}
			});
		}
	}

	public enum Stat {
		STRENGTH,
		CONTROL,
		BREATHING
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

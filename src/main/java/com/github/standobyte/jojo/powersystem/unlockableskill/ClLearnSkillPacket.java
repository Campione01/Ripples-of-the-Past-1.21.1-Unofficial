package com.github.standobyte.jojo.powersystem.unlockableskill;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.PowerData;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.TrainableAbility;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandTypePersistentData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClLearnSkillPacket implements CustomPacketPayload {
	public PowerClass<?> powerClass;
	public ResourceLocation powerType;
	public PacketType packetType;
	public String skillName;
	
	public static ClLearnSkillPacket learnSkill(PowerClass<?> powerClass, ResourceLocation powerType, String skillName) {
		return new ClLearnSkillPacket(powerClass, powerType, PacketType.LEARN, skillName);
	}
	
	public static ClLearnSkillPacket learnAll(PowerClass<?> powerClass, ResourceLocation powerType) {
		return new ClLearnSkillPacket(powerClass, powerType, PacketType.LEARN_ALL, null);
	}
	
	public static ClLearnSkillPacket resetAll(PowerClass<?> powerClass, ResourceLocation powerType) {
		return new ClLearnSkillPacket(powerClass, powerType, PacketType.RESET_ALL, null);
	}
	
	public static ClLearnSkillPacket trainSkill(PowerClass<?> powerClass, ResourceLocation powerType, String skillName) {
		return new ClLearnSkillPacket(powerClass, powerType, PacketType.TRAIN, skillName);
	}
	
	public ClLearnSkillPacket(PowerClass<?> powerClass, ResourceLocation powerType, PacketType packetType, String skillName) {
		this.powerClass = powerClass;
		this.powerType = powerType;
		this.packetType = packetType;
		this.skillName = skillName;
	}
	
	public static enum PacketType {
		LEARN,
		LEARN_ALL,
		RESET,
		RESET_ALL,
		TRAIN
	}
	
	
	private static CustomPacketPayload.Type<ClLearnSkillPacket> type;
	
	public static class Handler implements PacketsRegister.PacketOGHandler<ClLearnSkillPacket> {
		
		public Handler(ResourceLocation packetId) { 
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClLearnSkillPacket> type() {
			return type;
		}

		@Override
		public void encode(ClLearnSkillPacket packet, RegistryFriendlyByteBuf buf) {
			PowerClass.NETWORK_CODEC.encode(buf, packet.powerClass);
			ResourceLocation.STREAM_CODEC.encode(buf, packet.powerType);
			buf.writeEnum(packet.packetType);
			switch (packet.packetType) {
				case LEARN_ALL, RESET_ALL -> {}
				default -> buf.writeUtf(packet.skillName);
			}
		}

		@Override
		public ClLearnSkillPacket decode(RegistryFriendlyByteBuf buf) {
			PowerClass<?> powerClass = PowerClass.NETWORK_CODEC.decode(buf);
			ResourceLocation powerType = ResourceLocation.STREAM_CODEC.decode(buf);
			PacketType packetType = buf.readEnum(PacketType.class);
			return switch (packetType) {
				case LEARN_ALL, RESET_ALL -> {
					yield new ClLearnSkillPacket(powerClass, powerType, packetType, null);
				}
				default -> {
					String skillName = buf.readUtf();
					yield new ClLearnSkillPacket(powerClass, powerType, packetType, skillName);
				}
			};
		}
		

		@Override
		public void handle(ClLearnSkillPacket payload, IPayloadContext context) {
			Player player = context.player();
			Power<?> power = payload.powerClass.get(player);
			if (power != null && power.hasPower() && power.getPowerType().getId().equals(payload.powerType)) {
				PowerData powerData = power.getCurTypeData();
				if (powerData != null) {
					switch (payload.packetType) {
						case LEARN -> {
							if (payload.skillName != null && player.isCreative()
									&& payload.powerClass == PowerClass.PLAYER_POWER
									&& !powerData.isSkillUnlocked(payload.skillName)) {
								if (powerData._setSkillUnlocked(payload.skillName, true, true)) {
									powerData.syncOnUpdate(player);
								}
							}
							else {
								powerData.unlockSkill(power, payload.skillName);
							}
						}
						case LEARN_ALL -> {
							if (!player.isCreative()) {
								return;
							}
							if (power instanceof StandPower standPower) {
								standPower.skipProgression();
							}
							else {
								// unlock all skills and sync
								for (var skillEntry : powerData.getAllSkills().entrySet()) {
									UnlockableSkill skill = skillEntry.getValue();
									if (!skill.isStarting) {
										String skillName = skillEntry.getKey();
										powerData._setSkillUnlocked(skillName, true, false);
									}
								}
								powerData.syncOnUpdate(player);
							}
						}
//						case RESET -> {
//							// remove skill
//						}
						case RESET_ALL -> {
							if (!player.isCreative()) {
								return;
							}
							powerData.resetUnlockedSkills(power);
						}
						case TRAIN -> {
							if (player.isCreative() && power instanceof StandPower standPower
									&& powerData instanceof StandTypePersistentData standData) {
								trainSkillToMax(standPower, standData, payload.skillName);
							}
						}
					}
				}
			}
		}

		private static void trainSkillToMax(StandPower standPower, StandTypePersistentData data, String skillName) {
			if (skillName == null || !data.isSkillUnlocked(skillName) || standPower.getMoveset() == null) {
				return;
			}
			UnlockableSkill skill = data.getAllSkills().get(skillName);
			if (skill == null) {
				return;
			}
			if (trainAbility(standPower, data, skillName)) {
				data.syncOnUpdate(standPower.getUser());
				return;
			}
			for (String abilityName : skill.unlocksAbilities) {
				if (trainAbility(standPower, data, abilityName)) {
					data.syncOnUpdate(standPower.getUser());
					return;
				}
			}
		}

		private static boolean trainAbility(StandPower standPower, StandTypePersistentData data, String abilityName) {
			Ability ability = standPower.getMoveset().getAbility(abilityName);
			if (!(ability instanceof TrainableAbility trainable)) {
				return false;
			}
			String learningAbilityName = trainable.getLearningAbilityName();
			if (learningAbilityName == null || data.getAbilityLearningProgressPoints(learningAbilityName) < 0.0F) {
				return false;
			}
			float maxTraining = trainable.getMaxTrainingPoints(standPower);
			data.setAbilityLearningProgressPoints(learningAbilityName, maxTraining, maxTraining, standPower);
			JojoMod.getLogger().info(
					"Creative stand skill training maxed: player={}, stand={}, skillAbility={}, learningAbility={}, points={}/{}.",
					standPower.getUser() != null ? standPower.getUser().getScoreboardName() : "<unknown>",
					standPower.getPowerType() != null ? standPower.getPowerType().getId() : "<none>",
					abilityName, learningAbilityName, maxTraining, maxTraining);
			return true;
		}
		
	}
	
	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
	
}

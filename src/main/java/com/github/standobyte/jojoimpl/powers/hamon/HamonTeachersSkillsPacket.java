package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HamonTeachersSkillsPacket(boolean teacherNearby, Set<String> teacherSkills) implements CustomPacketPayload {
	private static final int MAX_TEACHER_SKILLS = 128;
	private static final int MAX_SKILL_NAME_LENGTH = 128;
	private static CustomPacketPayload.Type<HamonTeachersSkillsPacket> packetType;

	public HamonTeachersSkillsPacket {
		teacherSkills = Set.copyOf(teacherSkills);
		NetworkPayloadValidation.requireOutboundCollectionSize(
				teacherSkills.size(), MAX_TEACHER_SKILLS, "Hamon teacher skill");
		teacherSkills.forEach(skillName -> NetworkPayloadValidation.requireUtfLength(
				skillName, MAX_SKILL_NAME_LENGTH, "Hamon teacher skill name"));
	}

	public HamonTeachersSkillsPacket() {
		this(false, Set.of());
	}

	public HamonTeachersSkillsPacket(Set<String> teacherSkills) {
		this(true, Set.copyOf(teacherSkills));
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<HamonTeachersSkillsPacket> {
		public Handler(ResourceLocation packetId) {
			packetType = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<HamonTeachersSkillsPacket> type() {
			return packetType;
		}

		@Override
		public void encode(HamonTeachersSkillsPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.teacherNearby);
			if (packet.teacherNearby) {
				buf.writeVarInt(packet.teacherSkills.size());
				for (String skillName : packet.teacherSkills) {
					buf.writeUtf(skillName, MAX_SKILL_NAME_LENGTH);
				}
			}
		}

		@Override
		public HamonTeachersSkillsPacket decode(RegistryFriendlyByteBuf buf) {
			boolean teacherNearby = buf.readBoolean();
			if (!teacherNearby) {
				return new HamonTeachersSkillsPacket();
			}
			int size = NetworkPayloadValidation.requireCollectionSize(
					buf.readVarInt(), MAX_TEACHER_SKILLS, "Hamon teacher skill");
			Set<String> teacherSkills = new HashSet<>();
			for (int i = 0; i < size; i++) {
				teacherSkills.add(buf.readUtf(MAX_SKILL_NAME_LENGTH));
			}
			return new HamonTeachersSkillsPacket(teacherSkills);
		}

		@Override
		public void handle(HamonTeachersSkillsPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.setTeacherSkills(payload.teacherNearby
							? knownTeacherSkills(payload.teacherSkills)
							: null);
				});
			}
		}
	}

	static Set<String> knownTeacherSkills(Set<String> teacherSkills) {
		return knownTeacherSkills(
				teacherSkills, skillName -> ModHamonSkills.definitionFor(skillName) != null);
	}

	static Set<String> knownTeacherSkills(
			Set<String> teacherSkills, Predicate<String> isKnownSkill) {
		return teacherSkills.stream()
				.filter(isKnownSkill)
				.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

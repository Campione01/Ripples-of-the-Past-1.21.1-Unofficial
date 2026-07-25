package com.github.standobyte.jojoimpl.powers.hamon;

import java.util.HashSet;
import java.util.Set;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HamonTeachersSkillsPacket(boolean teacherNearby, Set<String> teacherSkills) implements CustomPacketPayload {
	private static CustomPacketPayload.Type<HamonTeachersSkillsPacket> packetType;

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
					buf.writeUtf(skillName);
				}
			}
		}

		@Override
		public HamonTeachersSkillsPacket decode(RegistryFriendlyByteBuf buf) {
			boolean teacherNearby = buf.readBoolean();
			if (!teacherNearby) {
				return new HamonTeachersSkillsPacket();
			}
			int size = buf.readVarInt();
			Set<String> teacherSkills = new HashSet<>();
			for (int i = 0; i < size; i++) {
				teacherSkills.add(buf.readUtf());
			}
			return new HamonTeachersSkillsPacket(teacherSkills);
		}

		@Override
		public void handle(HamonTeachersSkillsPacket payload, IPayloadContext context) {
			Player player = ClientProxy.getClientPlayer();
			if (player != null) {
				PlayerPower.getPowerData(player, ModPlayerPowers.HAMON).ifPresent(hamon -> {
					hamon.setTeacherSkills(payload.teacherNearby ? payload.teacherSkills : null);
				});
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return packetType;
	}
}

package com.github.standobyte.jojo.powersystem.standpower.packet;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.network.NetworkPayloadValidation;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrStandAbilityCooldownPacket(int entityId, boolean resetAll, String abilityName, int cooldown, int totalCooldown) implements CustomPacketPayload {
	private static final int MAX_ABILITY_NAME_LENGTH = 256;
	private static CustomPacketPayload.Type<TrStandAbilityCooldownPacket> type;

	public TrStandAbilityCooldownPacket {
		abilityName = NetworkPayloadValidation.requireUtfLength(
				abilityName, MAX_ABILITY_NAME_LENGTH, "Stand ability name");
	}

	public TrStandAbilityCooldownPacket(int entityId, String abilityName, int cooldown, int totalCooldown) {
		this(entityId, false, abilityName, cooldown, totalCooldown);
	}

	public static TrStandAbilityCooldownPacket resetAll(int entityId) {
		return new TrStandAbilityCooldownPacket(entityId, true, "", 0, 0);
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<TrStandAbilityCooldownPacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrStandAbilityCooldownPacket> type() {
			return type;
		}

		@Override
		public void encode(TrStandAbilityCooldownPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(packet.resetAll);
			buf.writeInt(packet.entityId);
			if (!packet.resetAll) {
				buf.writeUtf(packet.abilityName, MAX_ABILITY_NAME_LENGTH);
				buf.writeVarInt(packet.cooldown);
				buf.writeVarInt(packet.totalCooldown);
			}
		}

		@Override
		public TrStandAbilityCooldownPacket decode(RegistryFriendlyByteBuf buf) {
			boolean resetAll = buf.readBoolean();
			int entityId = buf.readInt();
			if (resetAll) {
				return TrStandAbilityCooldownPacket.resetAll(entityId);
			}
			return new TrStandAbilityCooldownPacket(
					entityId, buf.readUtf(MAX_ABILITY_NAME_LENGTH),
					buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public void handle(TrStandAbilityCooldownPacket payload, IPayloadContext context) {
			Entity entity = ClientProxy.getEntityById(payload.entityId);
			if (entity instanceof LivingEntity living) {
				StandPower standPower = StandPower.get(living);
				if (standPower != null) {
					if (payload.resetAll) {
						standPower.resetAbilityCooldowns();
					}
					else {
						standPower.setAbilityCooldown(payload.abilityName, payload.cooldown, payload.totalCooldown);
					}
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

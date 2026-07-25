package com.github.standobyte.jojo.network.c2s;

import com.github.standobyte.jojo.PacketsRegister;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClAngeloRockButtonPacket implements CustomPacketPayload {
	private static CustomPacketPayload.Type<ClAngeloRockButtonPacket> type;
	private final PacketType packetType;

	private ClAngeloRockButtonPacket(PacketType packetType) {
		this.packetType = packetType;
	}

	public static ClAngeloRockButtonPacket respawn() {
		return new ClAngeloRockButtonPacket(PacketType.RESPAWN);
	}

	public static ClAngeloRockButtonPacket grunt() {
		return new ClAngeloRockButtonPacket(PacketType.GRUNT);
	}

	private enum PacketType {
		RESPAWN,
		GRUNT
	}

	public static class Handler implements PacketsRegister.PacketOGHandler<ClAngeloRockButtonPacket> {
		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<ClAngeloRockButtonPacket> type() {
			return type;
		}

		@Override
		public void encode(ClAngeloRockButtonPacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeEnum(packet.packetType);
		}

		@Override
		public ClAngeloRockButtonPacket decode(RegistryFriendlyByteBuf buf) {
			return new ClAngeloRockButtonPacket(buf.readEnum(PacketType.class));
		}

		@Override
		public void handle(ClAngeloRockButtonPacket packet, IPayloadContext context) {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}
			Entity possessed = LivingComponentPossession.getEntityPossessedBy(player);
			if (possessed != null && possessed.getType() == ModEntityTypes.ANGELO_ROCK.get()) {
				switch (packet.packetType) {
				case RESPAWN:
					LivingComponentPossession.setPossessionTarget(player, null, null);
					player.invulnerableTime = 0;
					player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
					player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
					break;
				case GRUNT:
					possessed.playSound(ModSoundEvents.ANGELO_ROCK_GRUNT.get(), 1, 1);
					break;
				}
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

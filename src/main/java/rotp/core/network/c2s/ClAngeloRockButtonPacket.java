package rotp.core.network.c2s;

import rotp.core.PacketsRegister;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;

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
			if (context.player() instanceof ServerPlayer player) {
				handleOnServer(player, packet);
			}
		}
	}

	public static void handleOnServer(ServerPlayer player, ClAngeloRockButtonPacket packet) {
		Entity possessed = LivingComponentPossession.getEntityPossessedBy(player);
		if (possessed != null && possessed.getType() == ModEntityTypes.ANGELO_ROCK.get()) {
			switch (packet.packetType) {
			case RESPAWN:
				// 1.16 death message "rockRespawn"; ending the possession kills with it
				player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
				LivingComponentPossession.respawnFromAngeloRock(player);
				break;
			case GRUNT:
				possessed.playSound(ModSoundEvents.ANGELO_ROCK_GRUNT.get(), 1, 1);
				break;
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

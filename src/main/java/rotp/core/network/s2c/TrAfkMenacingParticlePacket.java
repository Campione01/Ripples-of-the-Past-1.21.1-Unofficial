package rotp.core.network.s2c;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.particle.type.OnomatopoeiaParticle;
import rotp.core.config.client.ClientModSettings;
import rotp.core.init.ModParticles;
import rotp.core.mixin.client.particle.LevelRendererParticleInvoker;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 SpawnParticlePacket with SpecialContext.AFK: one MENACING particle at an idle player's eyes,
 * drifting along its yaw. Receivers with the menacingParticles client setting off drop it.
 * The owner is the idle player, so that its own client can hide the glyph in first person; null is unknown.
 */
public record TrAfkMenacingParticlePacket(double x, double y, double z, float xSpeed, float ySpeed, float zSpeed,
		@Nullable UUID owner) implements CustomPacketPayload {
	public static final int INTERVAL_TICKS = 60;
	public static final long IDLE_MS = 30_000L;
	public static final int NO_INPUT_TICKS = 30 * 20;
	private static final float SPEED = 0.005F;

	public TrAfkMenacingParticlePacket(double x, double y, double z, float xSpeed, float ySpeed, float zSpeed) {
		this(x, y, z, xSpeed, ySpeed, zSpeed, null);
	}

	// 1.16 GameplayEventHandler.onPlayerTick: every 60 ticks, visible, idle for more than 30 s,
	// and no movement, jump or sneak key held for more than 30 s
	public static boolean shouldSend(Player player, long idleMs) {
		return player.tickCount % INTERVAL_TICKS == 0 && !player.isInvisible() && idleMs > IDLE_MS
				&& noInputTicks(PlayerMovementInputData.get(player)) > NO_INPUT_TICKS;
	}

	// 1.16 PlayerUtilCap.noClientInputTimer: 0 while a key is held, else ticks since the last one was released
	public static int noInputTicks(@Nullable PlayerMovementInputData input) {
		if (input == null) {
			return Integer.MAX_VALUE;
		}
		if (input.left != 0 || input.forward != 0 || input.jumping || input.shiftKeyDown) {
			return 0;
		}
		return Math.min(Math.min(input.leftTimer, input.forwardTimer),
				Math.min(input.jumpingTimer, input.shiftKeyDownTimer));
	}

	// 1.16: eye height, direction (cos yRot, 0.5, sin yRot) times 0.005
	public static TrAfkMenacingParticlePacket at(Player player) {
		float yRot = player.getYRot() * Mth.DEG_TO_RAD;
		return new TrAfkMenacingParticlePacket(player.getX(), player.getEyeY(), player.getZ(),
				Mth.cos(yRot) * SPEED, 0.5F * SPEED, Mth.sin(yRot) * SPEED, player.getUUID());
	}

	/** Server tick: sends to the player and its trackers when due; returns the sent packet or null. */
	@Nullable
	public static TrAfkMenacingParticlePacket tick(ServerPlayer player, long idleMs) {
		if (!shouldSend(player, idleMs)) {
			return null;
		}
		TrAfkMenacingParticlePacket packet = at(player);
		PacketDistributor.sendToPlayer(player, packet);
		PacketDistributor.sendToPlayersTrackingEntity(player, packet);
		return packet;
	}

	// 1.16 SpawnParticlePacket.handle: AFK particles only with menacingParticles on
	public static boolean shown(ClientModSettings.Settings settings) {
		return settings.menacingParticles;
	}

	/**
	 * Draw-time rule of the ownAfkMenacingFirstPerson client setting: only the glyphs of the local player,
	 * only while the camera sits in that player's own eyes. Glyphs of an unknown owner are never hidden.
	 */
	public static boolean hiddenInOwnFirstPerson(ClientModSettings.Settings settings, @Nullable UUID owner,
			@Nullable UUID localPlayer, @Nullable UUID cameraEntity, boolean firstPerson, boolean cameraDetached) {
		return !settings.ownAfkMenacingFirstPerson && owner != null
				&& owner.equals(localPlayer) && owner.equals(cameraEntity)
				&& firstPerson && !cameraDetached;
	}

	private static CustomPacketPayload.Type<TrAfkMenacingParticlePacket> type;

	public static class Handler implements PacketsRegister.PacketOGHandler<TrAfkMenacingParticlePacket> {

		public Handler(ResourceLocation packetId) {
			type = new CustomPacketPayload.Type<>(packetId);
		}

		@Override
		public Type<TrAfkMenacingParticlePacket> type() {
			return type;
		}

		@Override
		public void encode(TrAfkMenacingParticlePacket packet, RegistryFriendlyByteBuf buf) {
			buf.writeDouble(packet.x);
			buf.writeDouble(packet.y);
			buf.writeDouble(packet.z);
			buf.writeFloat(packet.xSpeed);
			buf.writeFloat(packet.ySpeed);
			buf.writeFloat(packet.zSpeed);
			buf.writeBoolean(packet.owner != null);
			if (packet.owner != null) {
				buf.writeUUID(packet.owner);
			}
		}

		@Override
		public TrAfkMenacingParticlePacket decode(RegistryFriendlyByteBuf buf) {
			return new TrAfkMenacingParticlePacket(buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean() ? buf.readUUID() : null);
		}

		@Override
		public void handle(TrAfkMenacingParticlePacket payload, IPayloadContext context) {
			ClientSide.handle(payload);
		}
	}

	// client-only classes stay out of the common handler
	private static final class ClientSide {
		static void handle(TrAfkMenacingParticlePacket payload) {
			if (!shown(ClientModSettings.getSettingsReadOnly())) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc.level == null) {
				return;
			}
			// what ClientLevel.addParticle runs (same quality, distance and limiter gates), but it returns the particle
			SimpleParticleType menacing = ModParticles.MENACING.get();
			Particle particle = ((LevelRendererParticleInvoker) mc.levelRenderer).jojo_ripples$addParticle(
					menacing, menacing.getOverrideLimiter(), payload.x, payload.y, payload.z,
					payload.xSpeed, payload.ySpeed, payload.zSpeed);
			if (particle instanceof OnomatopoeiaParticle glyph) {
				glyph.setAfkOwner(payload.owner);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

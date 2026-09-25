package rotp.core.network.s2c;

import javax.annotation.Nullable;

import rotp.core.PacketsRegister;
import rotp.core.client.ClientProxy;
import rotp.core.config.client.ClientModSettings;
import rotp.core.init.ModParticles;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 SpawnParticlePacket with SpecialContext.AFK: one MENACING particle at an idle player's eyes,
 * drifting along its yaw. Receivers with the menacingParticles client setting off drop it.
 */
public record TrAfkMenacingParticlePacket(double x, double y, double z, float xSpeed, float ySpeed, float zSpeed)
		implements CustomPacketPayload {
	public static final int INTERVAL_TICKS = 60;
	public static final long IDLE_MS = 30_000L;
	public static final int NO_INPUT_TICKS = 30 * 20;
	private static final float SPEED = 0.005F;

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
				Mth.cos(yRot) * SPEED, 0.5F * SPEED, Mth.sin(yRot) * SPEED);
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
		}

		@Override
		public TrAfkMenacingParticlePacket decode(RegistryFriendlyByteBuf buf) {
			return new TrAfkMenacingParticlePacket(buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat());
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
			Level level = ClientProxy.getClientWorld();
			if (level != null) {
				level.addParticle(ModParticles.MENACING.get(), payload.x, payload.y, payload.z,
						payload.xSpeed, payload.ySpeed, payload.zSpeed);
			}
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return type;
	}
}

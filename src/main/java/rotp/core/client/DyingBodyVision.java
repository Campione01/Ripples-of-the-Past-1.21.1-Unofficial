package rotp.core.client;

import javax.annotation.Nullable;

import rotp.core.JojoModLivingVariables;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Dying body "losing vision" math from 1.16 ClientEventHandler (renderLosingVision, dyingBodyLostVision).
 * No client-only classes here, so gametests can check it on a dedicated server.
 */
public final class DyingBodyVision {
	private DyingBodyVision() {}

	/** Vignette strength (0 none, 1 full) for this player; 0 when not a living, non-spectator dying body. */
	public static float losingVisionVignette(@Nullable Player player, float partialTick) {
		JojoModLivingVariables vars = dyingBodyVars(player);
		return vars != null
				? losingVisionVignette(vars.getDyingBodyTicksLeft(), vars.getDyingBodyProgress(), partialTick)
				: 0.0F;
	}

	public static float losingVisionVignette(int ticksLeft, float progress, float partialTick) {
		if (ticksLeft <= 0) {
			return 0.0F;
		}
		int timeLeft = ticksLeft - 1;
		float vignette = 0.0F;
		if (timeLeft <= 60) {
			// pulses every 20 ticks, then one slow 40-tick pulse over the last second
			float vignetteTime = (60 - timeLeft) + partialTick;
			float halfPeriod = timeLeft > 20 ? 10.0F : 20.0F;
			vignette = (Mth.cos(vignetteTime / halfPeriod * (float) Math.PI) + 1.0F) / 2.0F;
		}
		else if (progress > 0.8F) {
			vignette = 1.0F - 5.0F * (1.0F - progress);
		}
		return Mth.clamp(vignette, 0.0F, 1.0F);
	}

	/** How far the fog has closed in (0 untouched, 1 fully closed); 0 when not a living, non-spectator dying body. */
	public static float closingFogProgress(@Nullable Player player, float partialTick) {
		JojoModLivingVariables vars = dyingBodyVars(player);
		return vars != null ? closingFogProgress(vars.getDyingBodyTicksLeft(), partialTick) : 0.0F;
	}

	public static float closingFogProgress(int ticksLeft, float partialTick) {
		if (ticksLeft <= 0) {
			return 1.0F;
		}
		int timeLeft = ticksLeft - 1;
		if (timeLeft > 20) {
			return 0.0F;
		}
		return Mth.clamp((20 - timeLeft + partialTick) / 20.0F, 0.0F, 1.0F);
	}

	/** Pulls a fog plane distance toward the camera. */
	public static float closeIn(float planeDistance, float progress) {
		return Mth.lerp(progress, planeDistance, 0.0F);
	}

	@Nullable
	private static JojoModLivingVariables dyingBodyVars(@Nullable Player player) {
		if (player == null || player.isSpectator() || player.isDeadOrDying()) {
			return null;
		}
		JojoModLivingVariables vars = JojoModLivingVariables.get(player);
		return vars.isDyingBody() ? vars : null;
	}
}

package rotp.core.gametest;

import rotp.core.JojoModLivingVariables;
import rotp.core.client.DyingBodyVision;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClientEventHandler.renderLosingVision / dyingBodyLostVision: a dying body sees a vignette once
 * past 80% progress, pulsing over the last 60 ticks, and the fog closes in over the last 20 ticks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DyingBodyVisionGameTests {
	private DyingBodyVisionGameTests() {}

	@GameTest(template = "empty")
	public static void losingVisionVignetteFollows116(GameTestHelper helper) {
		// progress branch above 60 ticks left
		near(helper, DyingBodyVision.losingVisionVignette(1000, 0.5F, 0), 0, "no vignette below 80% progress");
		near(helper, DyingBodyVision.losingVisionVignette(1000, 0.9F, 0), 0.5F, "1 - 5 * (1 - progress) at 90%");
		near(helper, DyingBodyVision.losingVisionVignette(100, 1.0F, 0), 1, "full vignette at 100% progress");
		// fast pulse (half period 10 ticks) while more than 20 ticks are left
		near(helper, DyingBodyVision.losingVisionVignette(61, 0, 0), 1, "pulse peak at 60 ticks left");
		near(helper, DyingBodyVision.losingVisionVignette(56, 0, 0), 0.5F, "fast pulse half-way");
		near(helper, DyingBodyVision.losingVisionVignette(51, 0, 0), 0, "fast pulse trough");
		near(helper, DyingBodyVision.losingVisionVignette(51, 0, 0.5F), (float) (Math.cos(10.5 / 10 * Math.PI) + 1) / 2,
				"partial tick moves the pulse");
		// slow pulse (half period 20 ticks) over the last 20 ticks
		near(helper, DyingBodyVision.losingVisionVignette(21, 0, 0), 1, "slow pulse starts at the peak");
		near(helper, DyingBodyVision.losingVisionVignette(11, 0, 0), 0.5F, "slow pulse half-way");
		near(helper, DyingBodyVision.losingVisionVignette(1, 0, 0), 0, "slow pulse ends dark-free");
		near(helper, DyingBodyVision.losingVisionVignette(0, 1.0F, 0), 0, "no vignette with no ticks left");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void dyingBodyFogClosesInOverLast20Ticks(GameTestHelper helper) {
		near(helper, DyingBodyVision.closingFogProgress(22, 0.9F), 0, "fog untouched with 21 ticks left");
		near(helper, DyingBodyVision.closingFogProgress(21, 0.5F), 0.025F, "fog starts closing with 20 ticks left");
		near(helper, DyingBodyVision.closingFogProgress(11, 0), 0.5F, "fog half closed with 10 ticks left");
		near(helper, DyingBodyVision.closingFogProgress(1, 0.5F), 1, "fog closed on the last tick");
		near(helper, DyingBodyVision.closingFogProgress(0, 0), 1, "fog closed with no ticks left");
		near(helper, DyingBodyVision.closeIn(96, 0.5F), 48, "far plane pulled half-way in");
		near(helper, DyingBodyVision.closeIn(96, 1), 0, "far plane pulled all the way in");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void losingVisionOnlyForLivingDyingBody(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player spectator = GameTestPlayers.makeServerMockPlayer(helper, GameType.SPECTATOR);
		try {
			near(helper, DyingBodyVision.losingVisionVignette(player, 0), 0, "vignette without a dying body");
			near(helper, DyingBodyVision.closingFogProgress(player, 0), 0, "fog closes without a dying body");

			JojoModLivingVariables.get(player).setDyingBodyTimer(11, 100);
			near(helper, DyingBodyVision.losingVisionVignette(player, 0), 0.5F, "dying body vignette with 10 ticks left");
			near(helper, DyingBodyVision.closingFogProgress(player, 0), 0.5F, "dying body fog with 10 ticks left");

			JojoModLivingVariables.get(spectator).setDyingBodyTimer(11, 100);
			near(helper, DyingBodyVision.losingVisionVignette(spectator, 0), 0, "spectator gets the vignette");
			near(helper, DyingBodyVision.closingFogProgress(spectator, 0), 0, "spectator fog closes in");

			player.setHealth(0);
			near(helper, DyingBodyVision.losingVisionVignette(player, 0), 0, "dead player gets the vignette");
			near(helper, DyingBodyVision.closingFogProgress(player, 0), 0, "dead player fog closes in");
			near(helper, DyingBodyVision.losingVisionVignette(null, 0), 0, "no player");
			helper.succeed();
		}
		finally {
			player.discard();
			spectator.discard();
		}
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 2.0E-3F, what + ": expected " + expected + ", got " + actual);
	}
}

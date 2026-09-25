package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler.onWakeUp: waking up after the night was slept through
 * (wakeImmediately and updateWorld both false) set the Stand's stamina to its max.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandStaminaWakeUpGameTests {
	private StandStaminaWakeUpGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void sleepingThroughNightRefillsStandStamina(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			StandPower power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(!power.isStaminaInfinite(), "Stand stamina is infinite here, the test would prove nothing");
			float max = power.getMaxStamina();
			helper.assertTrue(max > 0, "Star Platinum has no max stamina");

			// leaving the bed by hand (updateLevel) does not refill
			power.setStamina(0);
			NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
			helper.assertTrue(power.getStamina() < 1.0E-3F,
					"leaving the bed before morning refilled Stand stamina: " + power.getStamina());
			// an immediate wake-up does not refill
			NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, true, false));
			helper.assertTrue(power.getStamina() < 1.0E-3F,
					"an immediate wake-up refilled Stand stamina: " + power.getStamina());
			// the morning wake-up after the night was skipped refills
			NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, false));
			helper.assertTrue(Math.abs(power.getStamina() - max) < 1.0E-3F,
					"sleeping through the night did not refill Stand stamina (1.16 onWakeUp): "
					+ power.getStamina() + " / " + max);
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}
}

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 giveStandFromInstance -> onNewPowerGiven ran setStamina(getMaxStamina() * 0.5F) on the server, so a new
 * Stand (arrow, disc, /stand give, evolution by clear + give) started at half stamina, not at 0. Tusk's act change
 * (context replace) keeps the stamina, as 1.16 Tusk was one Stand.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NewStandStaminaGameTests {
	private static final ResourceLocation STAR_PLATINUM = JojoMod.resLoc("star_platinum");
	private static final ResourceLocation THE_WORLD = JojoMod.resLoc("the_world");

	private NewStandStaminaGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void insertedStandStartsAtHalfStamina(GameTestHelper helper) {
		User user = new User(helper);
		try {
			user.give(STAR_PLATINUM);
			user.assertHalf("The inserted Stand (disc, legacy insert)");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	// the Stand arrow and /stand give set the Stand directly
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void setStandAfterLossStartsAtHalfStamina(GameTestHelper helper) {
		User user = new User(helper);
		try {
			user.give(STAR_PLATINUM);
			helper.assertTrue(StandPowerTransitions.extract(user.power, STAR_PLATINUM).applied()
					&& !user.power.hasPower(), "The legacy extract did not take the Stand");
			user.power.setStand(user.type(THE_WORLD));
			helper.assertTrue(user.power.getPowerType() == user.type(THE_WORLD), "setStand did not give The World");
			user.assertHalf("StandPower.setStand (arrow, /stand give)");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	// 1.16 evolutions ran clear() + give
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void legacyReplaceStartsTheEvolutionAtHalfStamina(GameTestHelper helper) {
		User user = new User(helper);
		try {
			user.give(STAR_PLATINUM);
			user.power.setStamina(user.power.getMaxStamina());
			StandPowerTransitions.Result replaced = StandPowerTransitions.replace(user.power, STAR_PLATINUM,
					user.instance(THE_WORLD));
			helper.assertTrue(replaced.applied() && user.power.getPowerType() == user.type(THE_WORLD),
					"The legacy replace did not evolve the Stand: " + replaced.status());
			user.assertHalf("The legacy replace (evolution)");
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void contextReplaceKeepsTheStamina(GameTestHelper helper) {
		User user = new User(helper);
		try {
			user.give(STAR_PLATINUM);
			// well under both Stands' max and away from half
			float kept = 100.0F;
			user.power.setStamina(kept);
			helper.assertTrue(!user.power.isStaminaInfinite() && user.power.getStamina() == kept,
					"Could not set the stamina: " + user.power.getStamina());
			StandPowerTransitions.Result replaced = StandPowerTransitions.replace(user.power, STAR_PLATINUM,
					user.instance(THE_WORLD), new StandPowerTransitions.TransitionContext(
							JojoMod.resLoc("gametest_act_change"), user.player));
			helper.assertTrue(replaced.applied() && user.power.getPowerType() == user.type(THE_WORLD),
					"The context replace did not change the Stand: " + replaced.status());
			helper.assertTrue(Math.abs(user.power.getStamina() - kept) < 1.0E-3F,
					"The context replace (act change) reset the stamina: " + user.power.getStamina()
					+ ", expected " + kept);
		}
		finally {
			user.close();
		}
		helper.succeed();
	}

	private static final class User {
		final GameTestHelper helper;
		final Player player;
		final StandPower power;

		User(GameTestHelper helper) {
			this.helper = helper;
			player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player.moveTo(at.x, at.y, at.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(player);
		}

		void give(ResourceLocation standId) {
			helper.assertTrue(StandPowerTransitions.insert(power, instance(standId)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
		}

		StandType type(ResourceLocation standId) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
			helper.assertTrue(type != null, "Missing registered Stand " + standId);
			return type;
		}

		StandInstance instance(ResourceLocation standId) {
			return new StandInstance(type(standId));
		}

		void assertHalf(String what) {
			helper.assertTrue(!power.isStaminaInfinite(), "Stand stamina is infinite here, the test would prove nothing");
			float max = power.getMaxStamina();
			helper.assertTrue(max > 0, "The Stand has no max stamina");
			helper.assertTrue(Math.abs(power.getStamina() - max * 0.5F) < 1.0E-3F,
					what + " did not start at half stamina: " + power.getStamina() + " / " + max);
		}

		void close() {
			if (power.isSummoned() && power.getPowerType() != null) {
				power.getPowerType().forceUnsummon(player, power);
			}
			player.discard();
		}
	}
}

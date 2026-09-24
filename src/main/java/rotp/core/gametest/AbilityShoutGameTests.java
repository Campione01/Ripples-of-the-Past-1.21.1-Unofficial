package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonPowerType;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Action#playVoiceLine said an action's shout only when the user was not sneaking, unless the action was a SHIFT
 * variation (StandEntityHeavyAttack also for a finisher; HamonBreath overrode the rule away). And the Resolve gate of an
 * ability, per-Stand overrides included, is readable by add-ons.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityShoutGameTests {
	private AbilityShoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void sneakingSkipsAShoutUnlessTheActionWasAShiftVariation(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = give(helper, player, "the_world");
			Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			ResourceLocation hierophant = JojoMod.resLoc("hierophant_green");
			ResourceLocation hamon = HamonPowerType.HAMON.getId();
			Ability splash = stand(ModStandAbilities.HG_EMERALD_SPLASH.get(), hierophant, "emerald_splash");
			Ability concentrated = stand(ModStandAbilities.HG_EMERALD_SPLASH_CONCENTRATED.get(), hierophant,
					"emerald_splash_concentrated");
			Ability syo = hamon(HamonPowerType.SUNLIGHT_YELLOW_OVERDRIVE.get(), hamon, "sunlight_yellow_overdrive");
			Ability syoBarrage = hamon(HamonPowerType.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get(), hamon,
					"sunlight_yellow_overdrive_barrage");
			Ability bubbleCutter = hamon(HamonPowerType.HAMON_BUBBLE_CUTTER.get(), hamon, "bubble_cutter");
			Ability gliding = hamon(HamonPowerType.HAMON_BUBBLE_CUTTER_GLIDING.get(), hamon, "bubble_cutter_gliding");
			Ability breath = hamon(HamonPowerType.HAMON_BREATH.get(), hamon, "hamon_breath");

			player.setShiftKeyDown(true);
			helper.assertTrue(player.isShiftKeyDown(), "the fixture could not sneak");
			for (Ability silent : new Ability[] { timeStop, splash, syo, bubbleCutter }) {
				helper.assertTrue(silent.skipsShoutWhileSneaking(player),
						silent.name() + " still shouts while its user sneaks (1.16 skipped it)");
			}
			helper.assertTrue(!timeStop.sayShout(player, ModSoundEvents.DIO_THE_WORLD),
					"sayShout said The World's line while its user sneaked");
			for (Ability shouting : new Ability[] { concentrated, syoBarrage, gliding, breath }) {
				helper.assertTrue(shouting.playsVoiceLineOnSneak() && !shouting.skipsShoutWhileSneaking(player),
						shouting.name() + " was a 1.16 SHIFT variation (or HamonBreath) and must shout while sneaking");
			}

			player.setShiftKeyDown(false);
			for (Ability any : new Ability[] { timeStop, splash, concentrated, syo, syoBarrage, bubbleCutter, gliding, breath }) {
				helper.assertTrue(!any.skipsShoutWhileSneaking(player), any.name() + " skipped a shout without sneaking");
			}
			helper.assertTrue(!timeStop.skipsShoutWhileSneaking(null), "a missing user cannot be sneaking");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theRequiredResolveLevelIncludesPerStandOverrides(GameTestHelper helper) {
		Player twUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Player spUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower theWorld = give(helper, twUser, "the_world");
			StandPower starPlatinum = give(helper, spUser, "star_platinum");
			Ability twTimeStop = theWorld.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			Ability spTimeStop = starPlatinum.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			// 1.16 THE_WORLD_TIME_STOP resolveLevelToUnlock(2), STAR_PLATINUM_TIME_STOP resolveLevelToUnlock(4)
			helper.assertTrue(twTimeStop.getRequiredResolveLevel(theWorld) == 2,
					"The World's time stop gate: " + twTimeStop.getRequiredResolveLevel(theWorld));
			helper.assertTrue(spTimeStop.getRequiredResolveLevel(starPlatinum) == 4,
					"Star Platinum's time stop gate: " + spTimeStop.getRequiredResolveLevel(starPlatinum));
			// the plain field hides the per-Stand override
			helper.assertTrue(twTimeStop.getResolveLevelToUnlock() == -1 && twTimeStop.getRequiredResolveLevel(null) == -1,
					"the time stop's own field is no longer the unset gate");
			Ability tsPunch = theWorld.getMoveset().getAbility("ts_punch");
			helper.assertTrue(tsPunch != null && tsPunch.getRequiredResolveLevel(theWorld) == 3,
					"The World's TS punch gate (1.16 resolveLevelToUnlock(3))");
		}
		finally {
			twUser.discard();
			spUser.discard();
		}
		helper.succeed();
	}

	private static StandPower give(GameTestHelper helper, Player player, String standName) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the " + standName + " test player");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(type != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		return power;
	}

	private static Ability stand(AbilityType<?> type, ResourceLocation standId, String name) {
		return type.createInstance(new AbilityId(PowerClass.STAND, standId, name));
	}

	private static Ability hamon(AbilityType<?> type, ResourceLocation hamonId, String name) {
		return type.createInstance(new AbilityId(PowerClass.PLAYER_POWER, hamonId, name));
	}
}

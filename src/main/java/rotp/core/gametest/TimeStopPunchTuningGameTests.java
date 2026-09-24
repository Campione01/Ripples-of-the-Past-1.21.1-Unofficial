package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 TheWorldTSHeavyAttack was built with its blink: the time skip cost half the blink's start cost plus the blink's
 * per-tick cost, and an add-on TS punch set its own staminaCost (Diego's 200, which trained nothing). 1.16
 * TimeStopInstant#getEntityTargetTeleportPos was an add-on hook (Shadow The World), and TimeStop.Builder#heldWalkSpeed
 * a per-time-stop setting. The core's own numbers must not move.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopPunchTuningGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_punch_tuning_gametest"));
	private static final float EPS = 1.0E-3F;

	private TimeStopPunchTuningGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void theWorldTsPunchKeepsItsCoreNumbers(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TheWorldTSPunchAbility punch = tsPunch(helper, power);
			helper.assertTrue(punch.getStaminaCost() == 50.0F && punch.trainsTimeStop(),
					"The World's TS punch must cost 50 and train its time stop (1.16 DEFAULT_STAMINA_COST)");
			// 225 * 0.8 * 0.5 and 9 * 100 / 100 * 0.8
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopBaseStaminaCost(power), 90.0F,
					"The World's TS punch time skip start");
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopStaminaCostTicking(power), 7.2F,
					"The World's TS punch time skip per tick");
			power.setStamina(500.0F);
			assertAffordable(helper, power, 90.0F, 7.2F, "The World");
			assertChargeWalks(helper, power, "The World");
		}
		finally {
			player.discard();
		}
		Player spUser = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, spUser, "star_platinum");
			helper.assertTrue(power.getMoveset().getAbility("ts_punch") == null, "Star Platinum gained a TS punch");
			assertChargeWalks(helper, power, "Star Platinum");
		}
		finally {
			spUser.discard();
		}
		helper.succeed();
	}

	// 1.16 heldWalkSpeed(0) for both; the owner's Batch907 boundary keeps the charge free to walk
	private static void assertChargeWalks(GameTestHelper helper, StandPower power, String stand) {
		Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
		helper.assertTrue(timeStop instanceof TimeStopAbility ability && ability.getHeldWalkSpeed() == 1.0F,
				stand + " time stop must keep its charge free to walk");
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void anAddOnBlinkPricesItsTsPunch(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TheWorldTSPunchAbility punch = tsPunch(helper, power);
			Ability blinkAbility = power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(blinkAbility instanceof TimeStopBlinkAbility, "The World lost its time_stop_blink");
			// Reworked's 1.16 time stop: staminaCost(300), staminaCostTick(8.875)
			((TimeStopBlinkAbility) blinkAbility).setBaseTimeStopStaminaCosts(300.0F, 8.875F);
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopBaseStaminaCost(power), 120.0F,
					"a TS punch time skip start from a 300 / 8.875 blink");
			assertNear(helper, TimeStopLearning.getTsPunchTimeStopStaminaCostTicking(power), 7.1F,
					"a TS punch time skip per tick from a 300 / 8.875 blink");
			power.setStamina(500.0F);
			assertAffordable(helper, power, 120.0F, 7.1F, "a 300 / 8.875 blink");

			// Diego's 1.16 THEWORLDTSHeavyAttack: staminaCost(200), no addLearningProgressPoints
			helper.assertTrue(punch.setStaminaCost(200.0F).setTrainsTimeStop(false) == punch
					&& punch.getStaminaCost() == 200.0F && !punch.trainsTimeStop(),
					"an add-on could not set its TS punch cost or turn its training off");
			boolean refused = false;
			try {
				punch.setStaminaCost(-1.0F);
			}
			catch (IllegalArgumentException expected) {
				refused = true;
			}
			helper.assertTrue(refused && punch.getStaminaCost() == 200.0F, "a negative TS punch cost was taken");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aBlinkTargetPositionHookReplacesTheBehindRule(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 4));
		try {
			Vec3 start = helper.absoluteVec(new Vec3(2.5D, 2.0D, 0.5D));
			player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			// the zombie faces south and looks 60 degrees down
			target.setYRot(0.0F);
			target.setXRot(60.0F);
			double widths = target.getBbWidth() + player.getBbWidth();
			Vec3 behind = target.position().subtract(Vec3.directionFromRotation(0.0F, 0.0F).scale(widths));
			assertVec(helper, teleportPos(blink, player, target), behind, "The World's blink behind a target (core rule)");

			// Shadow The World's 1.16 SHADOWWORLDTimeStopInstant: the whole look vector, pitch included
			BiFunction<LivingEntity, Entity, Vec3> lookBehind = (user, entity) ->
					entity.position().subtract(entity.getLookAngle().scale(entity.getBbWidth() + user.getBbWidth()));
			helper.assertTrue(blink.setEntityTargetTeleportPos(lookBehind) == blink, "the hook setter must chain");
			Vec3 pitched = target.position().subtract(target.getLookAngle().scale(widths));
			helper.assertTrue(Math.abs(pitched.y - target.getY()) > 0.5D, "the fixture's pitch does not move the landing");
			assertVec(helper, teleportPos(blink, player, target), pitched, "a blink with the add-on target position hook");

			blink.setEntityTargetTeleportPos(null);
			assertVec(helper, teleportPos(blink, player, target), behind, "a blink whose hook was cleared");
		}
		finally {
			player.discard();
			target.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aTimeStopHeldWalkSpeedAppliesWhileItCharges(GameTestHelper helper) {
		TimeStopAbility timeStop = ModStandAbilities.TIME_STOP.get().createInstance(
				new AbilityId(PowerClass.STAND, JojoMod.resLoc("the_world"), TimeStopLearning.TIME_STOP));
		TimeStopAbility.TimeStopAction action = new TimeStopAbility.TimeStopAction(timeStop);
		action.onSetPhase(ActionPhase.BUTTON_CHARGE);
		helper.assertTrue(action.userWalkSpeed == 1.0F, "the core time stop's charge locked walking: " + action.userWalkSpeed);
		// Shadow The World's 1.16 heldWalkSpeed(0.7F)
		helper.assertTrue(timeStop.setHeldWalkSpeed(0.7F) == timeStop && timeStop.getHeldWalkSpeed() == 0.7F,
				"an add-on could not set its time stop's held walk speed");
		action.onSetPhase(ActionPhase.BUTTON_CHARGE);
		helper.assertTrue(Math.abs(action.userWalkSpeed - 0.7F) < EPS, "the charge ignored the held walk speed: " + action.userWalkSpeed);
		action.onSetPhase(ActionPhase.PERFORM);
		helper.assertTrue(action.userWalkSpeed == 1.0F, "the held walk speed outlived the charge: " + action.userWalkSpeed);
		boolean refused = false;
		try {
			timeStop.setHeldWalkSpeed(1.5F);
		}
		catch (IllegalArgumentException expected) {
			refused = true;
		}
		helper.assertTrue(refused && timeStop.getHeldWalkSpeed() == 0.7F, "a held walk speed above 1 was taken");
		helper.succeed();
	}

	private static StandPower grantTrainedTimeStop(GameTestHelper helper, Player player, String standName) {
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the " + standName + " test player");
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		helper.assertTrue(standType != null, "Missing Stand type " + standName);
		StandPower power = PowerClass.STAND.attachGet(player);
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant " + standName);
		PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
				"Could not unlock " + standName + "'s time stop: " + unlocked);
		// trained to 100 ticks
		power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP,
				TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS - TimeStopLearning.MIN_TIME_STOP_TICKS,
				TimeStopLearning.getMaxTrainingPoints(power), power);
		helper.assertTrue(TimeStopLearning.getTimeStopTicks(power) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
				standName + " time stop did not train to 100 ticks: " + TimeStopLearning.getTimeStopTicks(power));
		return power;
	}

	private static TheWorldTSPunchAbility tsPunch(GameTestHelper helper, StandPower power) {
		Ability punch = power.getMoveset().getAbility("ts_punch");
		helper.assertTrue(punch instanceof TheWorldTSPunchAbility, "The World lost its ts_punch");
		return (TheWorldTSPunchAbility) punch;
	}

	// 1.16 TheWorldTSHeavyAttack: clamp(floor((stamina - start) / tick), 0, time stop ticks)
	private static void assertAffordable(GameTestHelper helper, StandPower power, float start, float tick, String what) {
		float multiplier = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(power);
		int expected = Mth.clamp(Mth.floor((power.getStamina() - start * multiplier) / (tick * multiplier)),
				0, TimeStopLearning.getTimeStopTicks(power));
		int actual = TimeStopLearning.getAffordableTsPunchTimeStopTicks(power);
		helper.assertTrue(actual == expected, what + " TS punch affordable ticks: expected " + expected + ", got " + actual);
	}

	private static Vec3 teleportPos(TimeStopBlinkAbility blink, LivingEntity user, Entity target) {
		try {
			Method method = TimeStopBlinkAbility.class.getDeclaredMethod("getEntityTargetTeleportPos",
					LivingEntity.class, Entity.class);
			method.setAccessible(true);
			return (Vec3) method.invoke(blink, user, target);
		}
		catch (ReflectiveOperationException error) {
			throw new AssertionError("Could not ask the blink for its entity target position", error);
		}
	}

	private static void assertNear(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}

	private static void assertVec(GameTestHelper helper, Vec3 actual, Vec3 expected, String what) {
		helper.assertTrue(actual.distanceTo(expected) < EPS, what + ": expected " + expected + ", got " + actual);
	}
}

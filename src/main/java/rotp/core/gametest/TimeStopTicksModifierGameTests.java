package rotp.core.gametest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopTicksModifiers;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Next Album TimeStopInstantAgingMixin rewrote TimeStopInstant#getMaxImpliedTicks at RETURN: an aged user
 * blinked a shorter reach, none from 85% aging. The core blink must apply add-on ticks modifiers to its final reach
 * and refuse a 0 reach before any stamina or cooldown is paid.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopTicksModifierGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_ticks_modifier_gametest"));
	// per test user, so parallel tests keep their full reach
	private static final Map<UUID, IntUnaryOperator> TEST_MODIFIERS = new ConcurrentHashMap<>();
	private static final Map<UUID, LivingEntity> SEEN_USERS = new ConcurrentHashMap<>();

	static {
		TimeStopTicksModifiers.register(JojoMod.resLoc("time_stop_ticks_modifier_gametest"), (user, ticks) -> {
			IntUnaryOperator modifier = TEST_MODIFIERS.get(user.getUUID());
			if (modifier == null) {
				return ticks;
			}
			SEEN_USERS.put(user.getUUID(), user);
			return modifier.applyAsInt(ticks);
		});
	}

	private TimeStopTicksModifierGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void anAddonTicksModifierShortensOrBlocksTheBlink(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		UUID id = player.getUUID();
		try {
			StandPower power = grantTrainedTimeStop(helper, player, "the_world");
			Ability blinkAbility = power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(blinkAbility instanceof TimeStopBlinkAbility, "The World lost its time_stop_blink");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) blinkAbility;
			power.setStamina(power.getMaxStamina());
			float stamina = power.getStamina();
			int fullReach = blink.getMaxImpliedTicks(power);
			helper.assertTrue(fullReach >= TimeStopLearning.MIN_TIME_STOP_TICKS + 3,
					"the fixture blink reach is too short: " + fullReach);
			helper.assertTrue(blink.checkSpecificConditions(power).isPositive(),
					"the fixture blink could not be used before any modifier");

			// shorter reach (1.16 aging below 85%), applied to the final stamina-capped value
			TEST_MODIFIERS.put(id, ticks -> ticks - 3);
			int shortReach = blink.getMaxImpliedTicks(power);
			helper.assertTrue(shortReach == fullReach - 3,
					"a ticks modifier did not shorten the blink: " + fullReach + " -> " + shortReach);
			helper.assertTrue(SEEN_USERS.get(id) == player, "the ticks modifier was not given the blink user");
			helper.assertTrue(blink.checkSpecificConditions(power).isPositive(),
					"a shortened blink must still be usable");

			// no reach (1.16 aging from 85%): refused before stamina and cooldown
			TEST_MODIFIERS.put(id, ticks -> 0);
			helper.assertTrue(blink.getMaxImpliedTicks(power) == 0,
					"a 0 ticks modifier left a reach: " + blink.getMaxImpliedTicks(power));
			helper.assertTrue(!blink.checkSpecificConditions(power).isPositive(),
					"a blink with no reach must not be usable");
			TEST_MODIFIERS.put(id, ticks -> -7);
			helper.assertTrue(blink.getMaxImpliedTicks(power) == 0,
					"a negative ticks modifier must give 0: " + blink.getMaxImpliedTicks(power));
			helper.assertTrue(power.getStamina() == stamina, "checking the blink spent stamina");

			TEST_MODIFIERS.remove(id);
			helper.assertTrue(blink.getMaxImpliedTicks(power) == fullReach
							&& blink.checkSpecificConditions(power).isPositive(),
					"the blink did not recover its reach once the modifier let go");
		}
		finally {
			TEST_MODIFIERS.remove(id);
			SEEN_USERS.remove(id);
			player.discard();
		}
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
		return power;
	}
}

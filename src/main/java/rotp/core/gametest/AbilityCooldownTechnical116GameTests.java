package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's Action.getCooldownTechnical, the technical part of an action's cooldown (Action.Builder.cooldown(technical,
 * additional); cooldown(n) was cooldown(0, n)), after which a 1.16 Stand-bearing mob picked its next action (rotp_mwp
 * StandAI.performAction :182, NonStandAI.performAction :212). The core's 1.16 splits: Star Finger cooldown(20, 60),
 * Hierophant Green's string bind cooldown(25, 100, 0.5F) and concentrated Emerald Splash cooldown(5, 60) (ModStandsInit
 * :168, :366, :378), Hamon's Zoom Punch cooldown(14, 0) (ModHamonActions :72), the Pillar Man's Ribs Blades cooldown(80,
 * 0) (ModPillarmanActions :48), and TimeStop.getCooldownTechnical :196-199, the stop's ticks. Ability#getCooldownTechnical
 * reports it (FEATURE_ABILITY_COOLDOWN_TECHNICAL_V1); the full cooldown stays technical + additional.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AbilityCooldownTechnical116GameTests {

	private AbilityCooldownTechnical116GameTests() {}

	private static final int NO_FULL_CHECK = -1;
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc(AbilityCooldownTechnical116GameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));

	private record Row(String power, String ability, int technical, int full) {}

	private static final List<Row> STAND_ROWS = List.of(
			new Row("star_platinum", "star_finger", 20, 80), new Row("star_platinum", "punch", 0, 0),
			new Row("star_platinum", "time_stop_blink", 0, NO_FULL_CHECK),
			new Row("hierophant_green", "string_bind", 25, 125), new Row("hierophant_green", "string_attack", 0, 0),
			new Row("hierophant_green", "emerald_splash_concentrated", 5, 65),
			new Row("hierophant_green", "emerald_splash", 0, 0),
			new Row("silver_chariot", "rapier_launch", 0, 100), new Row("the_world", "ts_punch", 0, 50));

	private static final List<Row> PLAYER_POWER_ROWS = List.of(
			new Row("hamon", "zoom_punch", 14, NO_FULL_CHECK), new Row("hamon", "hamon_overdrive", 0, NO_FULL_CHECK),
			new Row("pillar_man", "pillarman_ribs_blades", 80, NO_FULL_CHECK),
			new Row("pillar_man", "pillarman_regeneration", 0, NO_FULL_CHECK));

	// names the time stop by its moveset id, which GameTestBatchSourcePinGameTests counts as a press of it
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void theCoreAbilitiesReport116sTechnicalCooldownForAMobsNextPick(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<FakePlayer> users = new ArrayList<>();
		try {
			StandPower power = null;
			String current = null;
			for (Row row : STAND_ROWS) {
				if (!row.power().equals(current)) {
					current = row.power();
					power = standUser(helper, users, current);
				}
				Ability ability = power.getAbility(row.ability());
				helper.assertTrue(ability != null, "setup: " + current + " has no " + row.ability());
				check(wrong, row, ability.getCooldownTechnical(power), ability.getCooldown(power, -1));
			}

			for (String stand : new String[] { "star_platinum", "the_world" }) {
				power = standUser(helper, users, stand);
				Ability timeStop = power.getAbility(TimeStopLearning.TIME_STOP);
				helper.assertTrue(timeStop != null, "setup: " + stand + " has no time stop");
				int untrained = timeStop.getCooldownTechnical(power);
				TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
				power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 35.0F,
						TimeStopLearning.getMaxTrainingPoints(power), power);
				int trained = timeStop.getCooldownTechnical(power);
				helper.assertTrue(TimeStopLearning.getTimeStopTicks(power, TimeStopLearning.TIME_STOP) == 40,
						"setup: " + stand + "'s trained time stop does not last 40 ticks");
				if (untrained != 5 || trained != 40) {
					wrong.add(stand + " time_stop reports " + untrained + " untrained and " + trained
							+ " trained to 40 ticks, 1.16's the stop's ticks 5 and 40");
				}
			}

			for (Row row : PLAYER_POWER_ROWS) {
				PlayerPower playerPower = playerPowerUser(helper, users, row.power());
				Ability ability = playerPower.getAbility(row.ability());
				helper.assertTrue(ability != null, "setup: " + row.power() + " has no " + row.ability());
				check(wrong, row, ability.getCooldownTechnical(playerPower), NO_FULL_CHECK);
			}
		}
		finally {
			users.forEach(FakePlayer::discard);
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 Action.getCooldownTechnical, after which its Stand-bearing mobs picked"
				+ " their next action: " + String.join("; ", wrong));
		helper.succeed();
	}

	private static void check(List<String> wrong, Row row, int technical, int full) {
		if (technical != row.technical() || row.full() != NO_FULL_CHECK && full != row.full()) {
			wrong.add(row.power() + " " + row.ability() + " reports technical " + technical
					+ (row.full() != NO_FULL_CHECK ? " of a full " + full : "") + ", 1.16's " + row.technical()
					+ (row.full() != NO_FULL_CHECK ? " of " + row.full() : ""));
		}
	}

	private static FakePlayer addUser(GameTestHelper helper, List<FakePlayer> users) {
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CooldownTechnical"));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
		users.add(user);
		return user;
	}

	private static StandPower standUser(GameTestHelper helper, List<FakePlayer> users, String stand) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand));
		helper.assertTrue(type != null, "setup: no Stand " + stand);
		StandPower power = PowerClass.STAND.attachGet(addUser(helper, users));
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: could not give " + stand);
		helper.assertTrue(!power.isUserCreative(), "setup: the Stand user is in Creative");
		return power;
	}

	private static PlayerPower playerPowerUser(GameTestHelper helper, List<FakePlayer> users, String name) {
		PlayerPowerType<?> type = "hamon".equals(name) ? ModPlayerPowers.HAMON.get() : ModPlayerPowers.PILLAR_MAN.get();
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(addUser(helper, users));
		power.setPowerType(type);
		helper.assertTrue(power.getPowerType() == type, "setup: could not give " + name);
		return power;
	}
}

package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.abilities.HamonRebuffOverdriveAbility;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
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

	/*
	 * 1.16 Action.getCooldown was getCooldownTechnical + getCooldownAdditional, and getCooldownAdditional was 0 for a
	 * Creative user (Action :373-375): a Creative user kept Star Finger's 20 ticks, the string bind's 25, the concentrated
	 * Emerald Splash's 5, the Zoom Punch's 14 and Ribs Blades' 80, and had none for a plain cooldown(n). The Stand rows
	 * set the cooldown as a press does; the Hamon and Pillar Man rows perform the move, in Survival first to show the
	 * perform sets its cooldown.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aCreativeUserKeepsTheTechnicalPartOfACooldownAsIn116(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		List<FakePlayer> users = new ArrayList<>();
		try {
			StandPower power = null;
			String current = null;
			for (Row row : STAND_ROWS) {
				if (row.full() == NO_FULL_CHECK) {
					continue;
				}
				if (!row.power().equals(current)) {
					current = row.power();
					power = standUser(helper, users, current);
					((FakePlayer) power.getUser()).setGameMode(GameType.CREATIVE);
					helper.assertTrue(power.isUserCreative(), "setup: the " + current + " user is not in Creative");
				}
				Ability ability = power.getAbility(row.ability());
				helper.assertTrue(ability != null, "setup: " + current + " has no " + row.ability());
				int cooldown = ability.getCooldown(power, -1);
				ability.setCooldownOnUse(power);
				int set = power.getAbilityCooldown(row.ability());
				power.setAbilityCooldown(row.ability(), 0);
				if (cooldown != row.technical() || set != row.technical()) {
					wrong.add("Creative " + row.power() + " " + row.ability() + " gets a cooldown of " + cooldown + ", set "
							+ set + ", 1.16's technical " + row.technical() + " of " + row.full());
				}
			}

			for (PerformRow row : PERFORM_ROWS) {
				for (GameType mode : new GameType[] { GameType.SURVIVAL, GameType.CREATIVE }) {
					int cooldown = performedCooldown(helper, users, row, mode);
					int expected = mode == GameType.CREATIVE ? row.technical() : row.full();
					if (cooldown != expected) {
						wrong.add(mode.getName() + " " + row.power() + " " + row.ability() + " left a cooldown of " + cooldown
								+ ", 1.16's " + expected + " (technical " + row.technical() + " of " + row.full() + ")");
					}
				}
			}
		}
		finally {
			users.forEach(FakePlayer::discard);
		}
		helper.assertTrue(wrong.isEmpty(), "1.16 kept the technical part of a cooldown for a Creative user: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * Two moves whose 1.16 cooldown did not follow the technical part. Rebuff Overdrive was a plain cooldown(100)
	 * (ModHamonActions :178), which ContinuousActionInstance took from getCooldown as it started (0 in Creative) and
	 * scaled as it ended, so a Creative user got none, also when cancelled in its windup; Space Ripper Stingy Eyes set
	 * its 50 x ticks fired / 20 with setCooldownTimer as its lasers ended (VampirismSpaceRipperStingyEyes :69), Creative
	 * included.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void rebuffOverdriveAndStingyEyesSetTheirCooldownsInCreativeAsIn116(GameTestHelper helper) {
		List<FakePlayer> users = new ArrayList<>();
		List<String> wrong = new ArrayList<>();
		try {
			for (GameType mode : new GameType[] { GameType.SURVIVAL, GameType.CREATIVE }) {
				FakePlayer hamonUser = addUser(helper, users);
				hamonUser.setGameMode(mode);
				helper.assertTrue(playerPowerUser(hamonUser, "hamon") != null, "setup: could not give Hamon");
				HamonData hamon = PlayerPower.getPowerData(hamonUser, ModPlayerPowers.HAMON).orElseThrow();
				hamon.setBreathStability(hamon.getMaxBreathStability());
				hamon.setEnergy(hamon.getMaxEnergy());
				EntityActionAbility rebuffAbility = (EntityActionAbility) PowerClass.PLAYER_POWER.get(hamonUser).getAbility("rebuff_overdrive");
				EntityActionInstance rebuff = rebuffAbility.initActionOnAbilityUse(helper.getLevel(), hamonUser, hamonUser, null);
				LivingComponentAction hamonActions = LivingComponentAction.getComponent(hamonUser);
				hamonActions.setAction(rebuff, hamonUser, SyncType.NO_SYNC);
				for (int tick = 0; tick < 7; tick++) {
					hamonActions.tick();
				}
				helper.assertTrue(rebuff instanceof HamonRebuffOverdriveAbility.HamonRebuffOverdrive overdrive
						&& overdrive.canCancel(), "setup: " + mode.getName() + " Rebuff Overdrive is not in its windup: "
								+ rebuff.getPhase() + " " + rebuff.getPhaseTick());
				((HamonRebuffOverdriveAbility.HamonRebuffOverdrive) rebuff).cancel();
				int rebuffCooldown = hamon.getAbilityCooldown("rebuff_overdrive");
				if (mode == GameType.CREATIVE ? rebuffCooldown != 0 : rebuffCooldown <= 0) {
					wrong.add(mode.getName() + " Rebuff Overdrive cancelled in its windup left a cooldown of " + rebuffCooldown
							+ ", 1.16's " + (mode == GameType.CREATIVE ? "0" : "some"));
				}

				FakePlayer vampire = addUser(helper, users);
				vampire.setGameMode(mode);
				PlayerPower vampirism = PowerClass.PLAYER_POWER.attachGet(vampire);
				vampirism.setPowerType(ModPlayerPowers.VAMPIRISM.get());
				VampirismData data = PlayerPower.getPowerData(vampire, ModPlayerPowers.VAMPIRISM).orElseThrow();
				data.setVampireFullPower(true, vampire);
				VampirismState.get(vampire).blood().setCurrent(500.0F);
				data.setBloodLevel(VampirismState.get(vampire).blood().current());
				EntityActionAbility eyesAbility = (EntityActionAbility) vampirism.getAbility("vampirism_space_ripper_stingy_eyes");
				EntityActionInstance eyes = eyesAbility.initActionOnAbilityUse(helper.getLevel(), vampire, vampire, null);
				LivingComponentAction vampireActions = LivingComponentAction.getComponent(vampire);
				vampireActions.setAction(eyes, vampire, SyncType.NO_SYNC);
				// its charge run to its end and let go, as a mob's held press is fired
				eyes.setPhase(eyes.getPhase(), (int) Math.ceil(eyes.getCurPhaseLength()));
				eyes.onKeyRelease(vampire);
				for (int tick = 0; tick < 60 && !eyes.isOver(); tick++) {
					vampireActions.tick();
				}
				int eyesCooldown = data.getAbilityCooldown("vampirism_space_ripper_stingy_eyes");
				helper.getLevel().getEntities((Entity) null, vampire.getBoundingBox().inflate(32.0D),
						entity -> entity instanceof Projectile projectile && projectile.getOwner() == vampire).forEach(Entity::discard);
				if (!eyes.isOver() || eyesCooldown != 50) {
					wrong.add(mode.getName() + " Space Ripper Stingy Eyes fired its 20 ticks and left a cooldown of " + eyesCooldown
							+ " (over " + eyes.isOver() + "), 1.16's 50 x 20 / 20 = 50");
				}
			}
		}
		finally {
			users.forEach(FakePlayer::discard);
		}
		helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
		helper.succeed();
	}

	private record PerformRow(String power, String ability, int technical, int full) {}

	private static final List<PerformRow> PERFORM_ROWS = List.of(
			new PerformRow("hamon", "zoom_punch", 14, 14), new PerformRow("hamon", "turquoise_blue_overdrive", 0, 10),
			new PerformRow("pillar_man", "pillarman_ribs_blades", 80, 80),
			new PerformRow("pillar_man", "pillarman_horn_attack", 0, 60));

	// the move set as a press sets it and run to its end; the cooldown its perform left, its own entities discarded
	private static int performedCooldown(GameTestHelper helper, List<FakePlayer> users, PerformRow row, GameType mode) {
		FakePlayer user = addUser(helper, users);
		user.setGameMode(mode);
		PlayerPower power = playerPowerUser(user, row.power());
		helper.assertTrue(power != null, "setup: could not give " + row.power());
		boolean hamon = "hamon".equals(row.power());
		if (hamon) {
			HamonData data = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			data.setBreathStability(data.getMaxBreathStability());
			data.setEnergy(data.getMaxEnergy());
		}
		else {
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2, user);
			data.setMode(PillarmanMode.NONE, user);
			data.setEnergy(user, 100.0F);
		}
		Ability ability = power.getAbility(row.ability());
		helper.assertTrue(ability instanceof EntityActionAbility, "setup: " + row.power() + " has no " + row.ability());
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, user, null);
		helper.assertTrue(action != null, "setup: " + row.ability() + " did not start");
		component.setAction(action, user, SyncType.NO_SYNC);
		boolean performed = false;
		for (int tick = 0; tick < 20 && !action.isOver(); tick++) {
			performed |= action.getPhase() == ActionPhase.PERFORM;
			component.tick();
		}
		helper.assertTrue(action.isOver() && (performed || action.phasesLength.getFloat(ActionPhase.PERFORM) > 0),
				"setup: " + mode.getName() + " " + row.ability() + " did not perform and end: phase " + action.getPhase());
		int cooldown = hamon
				? PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow().getAbilityCooldown(row.ability())
				: PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow().getAbilityCooldown(row.ability());
		helper.getLevel().getEntities((Entity) null, user.getBoundingBox().inflate(32.0D),
				entity -> entity instanceof Projectile projectile && projectile.getOwner() == user).forEach(Entity::discard);
		return cooldown;
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
		PlayerPower power = playerPowerUser(addUser(helper, users), name);
		helper.assertTrue(power != null, "setup: could not give " + name);
		return power;
	}

	private static PlayerPower playerPowerUser(FakePlayer user, String name) {
		PlayerPowerType<?> type = "hamon".equals(name) ? ModPlayerPowers.HAMON.get() : ModPlayerPowers.PILLAR_MAN.get();
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
		power.setPowerType(type);
		return power.getPowerType() == type ? power : null;
	}
}

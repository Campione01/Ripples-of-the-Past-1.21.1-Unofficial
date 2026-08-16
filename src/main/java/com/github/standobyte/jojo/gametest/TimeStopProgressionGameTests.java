package com.github.standobyte.jojo.gametest;

import java.util.Optional;

import com.github.standobyte.jojo.api.power.PowerSkillUnlocks;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismState;
import com.github.standobyte.jojoimpl.powers.zombie.ZombieData;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopProgressionGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_progression_gametest"));

	private TimeStopProgressionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void timeStopTrainingPersistsAcrossSurvivalReload(
			GameTestHelper helper) {
		assertProgressionAndPersistence(
				helper, JojoMod.resLoc("the_world"), 20, 2.0F, 1_000_001);
		assertProgressionAndPersistence(
				helper, JojoMod.resLoc("star_platinum"), 20, 5.0F, 1_000_002);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void timeStopFullChargeTracksTrainingAndRace(
			GameTestHelper helper) {
		assertRaceDuration(helper, null, false, 1,
				TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS, "human");
		assertRaceDuration(helper, ModPlayerPowers.VAMPIRISM.get(), false, 1,
				TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS, "low-blood vampire");
		assertRaceDuration(helper, ModPlayerPowers.VAMPIRISM.get(), true, 1,
				TimeStopLearning.VAMPIRE_MAX_TIME_STOP_TICKS, "high-blood vampire");
		assertRaceDuration(helper, ModPlayerPowers.ZOMBIE.get(), false, 1,
				TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS, "low-saturation zombie");
		assertRaceDuration(helper, ModPlayerPowers.ZOMBIE.get(), true, 1,
				TimeStopLearning.ZOMBIE_MAX_TIME_STOP_TICKS, "high-saturation zombie");
		assertRaceDuration(helper, ModPlayerPowers.PILLAR_MAN.get(), true, 1,
				TimeStopLearning.PILLARMAN_MAX_TIME_STOP_TICKS, "stage-one Pillar Man");
		assertRaceDuration(helper, ModPlayerPowers.PILLAR_MAN.get(), true, 3,
				TimeStopLearning.PILLARMAN_MAX_TIME_STOP_TICKS
						+ TimeStopLearning.PILLARMAN_STAGE_TIME_STOP_BONUS_TICKS * 2,
				"stage-three Pillar Man");
		helper.succeed();
	}

	private static void assertRaceDuration(
			GameTestHelper helper,
			PlayerPowerType<?> race,
			boolean enhancedState,
			int pillarmanStage,
			int expectedMaxTicks,
			String profile) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(player),
					"Could not add " + profile + " duration test player");
			StandPower power = grantTimeStopStand(helper, player,
					JojoMod.resLoc("the_world"));
			if (race != null) {
				PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(player);
				playerPower.setPowerType(race);
				if (race == ModPlayerPowers.VAMPIRISM.get()) {
					var blood = VampirismState.get(player).blood();
					blood.setCurrent(enhancedState ? blood.max() : 0.0F);
				}
				else if (race == ModPlayerPowers.ZOMBIE.get()) {
					ZombieData data = playerPower
							.getCurTypeData(ModPlayerPowers.ZOMBIE)
							.orElseThrow();
					data.setEnergy(player,
							enhancedState ? data.getMaxEnergy(player) : 0.0F);
				}
				else if (race == ModPlayerPowers.PILLAR_MAN.get()) {
					PillarmanData data = playerPower
							.getCurTypeData(ModPlayerPowers.PILLAR_MAN)
							.orElseThrow();
					data.setEvolutionStage(pillarmanStage, player);
				}
			}

			helper.assertTrue(
					TimeStopLearning.getSavedTimeStopTicks(power)
							== TimeStopLearning.MIN_TIME_STOP_TICKS,
					profile + " fresh duration did not start at the training baseline");
			power.getCurTypeData().setAbilityLearningProgressPoints(
					TimeStopLearning.TIME_STOP, 25.0F,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			helper.assertTrue(
					TimeStopLearning.getSavedTimeStopTicks(power) == 30,
					profile + " duration did not grow with training points");

			int maxTrainingPoints = TimeStopLearning.getMaxTrainingPoints(power);
			power.getCurTypeData().setAbilityLearningProgressPoints(
					TimeStopLearning.TIME_STOP, maxTrainingPoints,
					maxTrainingPoints, power);
			helper.assertTrue(
					TimeStopLearning.getMaxTimeStopTicks(power) == expectedMaxTicks,
					profile + " maximum duration drifted");
			helper.assertTrue(
					TimeStopLearning.getSavedTimeStopTicks(power) == expectedMaxTicks,
					profile + " full training did not reach its race maximum");
			helper.assertTrue(
					TimeStopLearning.getReleasedTimeStopTicks(
							expectedMaxTicks, 20, 20) == expectedMaxTicks,
					profile + " full charge did not retain its trained maximum");
			helper.assertTrue(
					TimeStopLearning.getReleasedTimeStopTicks(
							expectedMaxTicks, 10, 20)
								== (expectedMaxTicks + 1) / 2,
					profile + " half charge did not scale from its trained maximum");
		}
		finally {
			player.discard();
		}
	}

	private static StandPower grantTimeStopStand(
			GameTestHelper helper, Player player, ResourceLocation standId) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
		helper.assertTrue(standType != null, "Missing Stand type " + standId);
		StandPower power = PowerClass.STAND.attachGet(player);
		StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
				power, new StandInstance(standType));
		helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
				"Could not grant " + standId + ": " + inserted.status());
		PowerSkillUnlocks.Result unlocked =
				TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		helper.assertTrue(
				unlocked == PowerSkillUnlocks.Result.UNLOCKED
						|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
				"Could not unlock Time Stop for " + standId + ": " + unlocked);
		return power;
	}

	private static void assertProgressionAndPersistence(
			GameTestHelper helper,
			ResourceLocation standId,
			int elapsedTicks,
			float expectedPoints,
			int instanceId) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(standId);
		helper.assertTrue(standType != null, "Missing Stand type " + standId);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Player restoredPlayer = null;
		try {
			helper.assertTrue(
					helper.getLevel().addFreshEntity(player),
					"Could not add logical-server test player for " + standId);
			StandPower power = PowerClass.STAND.attachGet(player);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
					power, new StandInstance(standType));
			helper.assertTrue(
					inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant " + standId + " on the logical server: "
							+ inserted.status());

			PowerSkillUnlocks.Result unlocked =
					TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(
					unlocked == PowerSkillUnlocks.Result.UNLOCKED
							|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
					"Could not unlock Time Stop for " + standId + ": " + unlocked);
			power.setResolveLevel(power.getMaxResolveLevel());
			power.getCurTypeData().setAbilityLearningProgressPoints(
					TimeStopLearning.TIME_STOP,
					0.0F,
					TimeStopLearning.getMaxTrainingPoints(power),
					power);
		assertPoints(helper, power, 0.0F, standId + " initial training");
		helper.assertTrue(
				PowerClass.STAND.get(player) == power,
				standId + " logical-server Stand attachment drifted");
		helper.assertTrue(
				power.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
				standId + " Time Stop ability was not actually unlocked");
		helper.assertTrue(
				power.getMoveset().getAbility(TimeStopLearning.TIME_STOP) != null,
				standId + " Time Stop ability is absent from the live moveset");
		helper.assertTrue(
				TimeStopLearning.learningPerTick(power) > 0.0F,
				standId + " has a non-positive Time Stop learning rate");

			TimeStopState state = new TimeStopState(helper.getLevel());
			TimeStopState.Instance instance = new TimeStopState.Instance(
					instanceId,
					elapsedTicks,
					elapsedTicks,
					new ChunkPos(player.blockPosition()),
					1,
					player.getId(),
					"time_stop_progression_gametest",
					Optional.of(standId),
					Optional.empty(),
					player.getId(),
					player.getId(),
					false,
					false,
					0.0F,
					0,
					false);
			helper.assertTrue(
					state.tryPutInstance(instance),
					"Production Time Stop state rejected " + standId);
			for (int tick = 0; tick < elapsedTicks; tick++) {
				state.tickLifecycle();
				if (tick + 1 < elapsedTicks) {
					int completedTicks = tick + 1;
					helper.assertTrue(
							state.getInstance(instanceId)
									.filter(active -> active.ticksPassed() == completedTicks)
									.isPresent(),
							standId + " Time Stop ended before tick " + completedTicks);
				}
			}
			helper.assertTrue(
					state.getInstance(instanceId).isEmpty(),
					"Time Stop instance cleanup failed for " + standId);
		assertPoints(helper, power, expectedPoints, standId + " settled training");

			state.removeInstance(instanceId);
			assertPoints(helper, power, expectedPoints,
					standId + " duplicate cleanup training");

			int savedTicks = TimeStopLearning.getSavedTimeStopTicks(power);
			player.getAbilities().instabuild = true;
			assertPoints(helper, power, expectedPoints,
					standId + " creative transition training");
			helper.assertTrue(
					TimeStopLearning.getTimeStopTicks(power)
							== TimeStopLearning.CREATIVE_TIME_STOP_TICKS,
					standId + " creative template duration was not applied");
			helper.assertTrue(
					TimeStopLearning.getSavedTimeStopTicks(power) == savedTicks,
					standId + " creative mode changed saved training duration");
			player.getAbilities().instabuild = false;
			assertPoints(helper, power, expectedPoints,
					standId + " survival return training");
			helper.assertTrue(
					TimeStopLearning.getTimeStopTicks(power) == savedTicks,
					standId + " survival return did not restore saved duration");

			HolderLookup.Provider registries = helper.getLevel().registryAccess();
			CompoundTag saved = power.serializeNBT(registries);
			restoredPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
			helper.assertTrue(
					helper.getLevel().addFreshEntity(restoredPlayer),
					"Could not add reloaded logical-server test player for " + standId);
			StandPower restored = PowerClass.STAND.attachGet(restoredPlayer);
			restored.deserializeNBT(registries, saved);
			restored.afterNbtRead();
			helper.assertTrue(
					restored.getStandInstance()
							.map(StandInstance::getStandId)
							.filter(standId::equals)
							.isPresent(),
					"Reloaded Stand identity drifted for " + standId);
			helper.assertTrue(
					restored.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
					"Reloaded Time Stop unlock was lost for " + standId);
		assertPoints(helper, restored, expectedPoints,
				standId + " reloaded training");
		helper.assertTrue(
				TimeStopLearning.getSavedTimeStopTicks(restored)
						== TimeStopLearning.MIN_TIME_STOP_TICKS
								+ (int) Math.floor(expectedPoints),
				"Reloaded trained duration drifted for " + standId);
		}
		finally {
			if (restoredPlayer != null) {
				restoredPlayer.discard();
			}
			player.discard();
		}
	}

	private static void assertPoints(
			GameTestHelper helper,
			StandPower power,
			float expected,
			String phase) {
		float actual = power.getCurTypeData()
				.getAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP);
		helper.assertTrue(
				Math.abs(expected - actual) <= 0.0001F,
				phase + " drifted: expected=" + expected + ", actual=" + actual);
	}
}

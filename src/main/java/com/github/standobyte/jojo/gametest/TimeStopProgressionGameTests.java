package com.github.standobyte.jojo.gametest;

import java.util.Optional;

import com.github.standobyte.jojo.api.power.PowerSkillUnlocks;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopLearning;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;

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

			player.getAbilities().instabuild = true;
			assertPoints(helper, power, expectedPoints,
					standId + " creative transition training");
			player.getAbilities().instabuild = false;
			assertPoints(helper, power, expectedPoints,
					standId + " survival return training");

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

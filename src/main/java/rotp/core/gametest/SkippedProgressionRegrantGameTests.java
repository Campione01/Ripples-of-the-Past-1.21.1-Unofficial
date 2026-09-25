package rotp.core.gametest;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** 1.16 TypeSpecificData.onPowerGiven: a skipped Stand is skipped again when a non-Stand power is gained. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkippedProgressionRegrantGameTests {
	private static final ResourceLocation THE_WORLD = JojoMod.resLoc("the_world");

	private SkippedProgressionRegrantGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void skippedStandRegainsRaceTimeStopMax(GameTestHelper helper) {
		Player player = makePlayer(helper, true);
		Player reloaded = null;
		Player cloned = null;
		try {
			StandPower power = grantStand(helper, player);
			power.skipProgression();
			helper.assertTrue(power.wasProgressionSkipped(), "skipProgression did not mark the Stand as skipped");
			helper.assertTrue(TimeStopLearning.getSavedTimeStopTicks(power) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
					"Skipped human Time Stop is not at the human maximum");

			PowerClass.PLAYER_POWER.attachGet(player).setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			int raceMax = TimeStopLearning.getMaxTrainingPoints(power) + TimeStopLearning.MIN_TIME_STOP_TICKS;
			helper.assertTrue(raceMax > TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
					"Pillar Man did not raise the Time Stop maximum: " + raceMax);
			helper.assertTrue(TimeStopLearning.getSavedTimeStopTicks(power) == raceMax,
					"Gaining Pillar Man did not re-skip the Stand: " + TimeStopLearning.getSavedTimeStopTicks(power)
					+ " of " + raceMax + " ticks");

			// the flag is saved and kept on respawn, as 1.16 "Skipped"
			HolderLookup.Provider registries = helper.getLevel().registryAccess();
			CompoundTag saved = power.serializeNBT(registries);
			reloaded = makePlayer(helper, false);
			StandPower restored = PowerClass.STAND.attachGet(reloaded);
			restored.deserializeNBT(registries, saved);
			helper.assertTrue(restored.wasProgressionSkipped(), "Skipped flag was lost on reload");
			cloned = makePlayer(helper, false);
			power.onPlayerClone(cloned, false);
			helper.assertTrue(PowerClass.STAND.attachGet(cloned).wasProgressionSkipped(), "Skipped flag was lost on clone");

			// removing the Stand drops the flag, as 1.16 clear()
			StandPowerTransitions.Result extracted = StandPowerTransitions.extract(power, THE_WORLD);
			helper.assertTrue(extracted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not remove the Stand: " + extracted.status());
			helper.assertTrue(!power.wasProgressionSkipped(), "Removing the Stand kept the skipped flag");
			helper.succeed();
		}
		finally {
			if (cloned != null) cloned.discard();
			if (reloaded != null) reloaded.discard();
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void creativeGrantSkipsProgression(GameTestHelper helper) {
		Player creative = makePlayer(helper, true);
		try {
			// 1.16 onNewPowerGiven: a creative grant skips even with the config off
			StandPower power = grantStand(helper, creative);
			helper.assertTrue(power.wasProgressionSkipped(), "A creative Stand grant did not skip progression");
			helper.assertTrue(TimeStopLearning.getSavedTimeStopTicks(power) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
					"Creative grant saved " + TimeStopLearning.getSavedTimeStopTicks(power) + " Time Stop ticks, not the human maximum");

			// 1.16 TypeSpecificData.onPowerGiven: then re-skipped to the race maximum
			PowerClass.PLAYER_POWER.attachGet(creative).setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			int raceMax = TimeStopLearning.getMaxTrainingPoints(power) + TimeStopLearning.MIN_TIME_STOP_TICKS;
			helper.assertTrue(raceMax > TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
					"Pillar Man did not raise the Time Stop maximum: " + raceMax);
			helper.assertTrue(TimeStopLearning.getSavedTimeStopTicks(power) == raceMax,
					"A creative grant was not re-skipped by Pillar Man: " + TimeStopLearning.getSavedTimeStopTicks(power)
					+ " of " + raceMax + " ticks");
			helper.succeed();
		}
		finally {
			creative.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void unskippedOrSurvivalStandKeepsTraining(GameTestHelper helper) {
		if (JojoModConfig.getCommonConfigInstance(false).skipStandProgression.get()) {
			// with the config on every Stand is skipped at grant, so neither negative case applies
			helper.succeed();
			return;
		}
		Player unskippedUser = makePlayer(helper, false);
		Player survival = makePlayer(helper, false);
		try {
			// survival grant without the config: 1.16 playerSkipsActionTraining was false
			StandPower unskipped = grantStand(helper, unskippedUser);
			helper.assertTrue(!unskipped.wasProgressionSkipped(), "A survival Stand grant skipped progression");
			int trainedTicks = TimeStopLearning.getSavedTimeStopTicks(unskipped);
			// creative later: 1.16 re-skipped only when wasProgressionSkipped()
			unskippedUser.getAbilities().instabuild = true;
			PowerClass.PLAYER_POWER.attachGet(unskippedUser).setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			helper.assertTrue(!unskipped.wasProgressionSkipped()
					&& TimeStopLearning.getSavedTimeStopTicks(unskipped) == trainedTicks,
					"An unskipped Stand was skipped by gaining Pillar Man");

			// skipped survival Stand without the config: not re-skipped
			StandPower skipped = grantStand(helper, survival);
			skipped.skipProgression();
			PowerClass.PLAYER_POWER.attachGet(survival).setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			helper.assertTrue(TimeStopLearning.getSavedTimeStopTicks(skipped) == TimeStopLearning.HUMAN_MAX_TIME_STOP_TICKS,
					"A survival Stand was re-skipped without the skipStandProgression config");
			helper.succeed();
		}
		finally {
			unskippedUser.getAbilities().instabuild = false;
			survival.discard();
			unskippedUser.discard();
		}
	}

	private static Player makePlayer(GameTestHelper helper, boolean creative) {
		Player player = helper.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
		player.getAbilities().instabuild = creative;
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add test player");
		return player;
	}

	private static StandPower grantStand(GameTestHelper helper, Player player) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(THE_WORLD);
		helper.assertTrue(standType != null, "Missing Stand type " + THE_WORLD);
		StandPower power = PowerClass.STAND.attachGet(player);
		StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
		helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
				"Could not grant " + THE_WORLD + ": " + inserted.status());
		return power;
	}
}

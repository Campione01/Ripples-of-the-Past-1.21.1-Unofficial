package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandPower.unlockAction gave a trained action its full training only while playerSkipsActionTraining (Creative
 * or the skipStandProgression config); an earlier skipped progression did not count, so a Survival user whose
 * progression was skipped gets a later unlock untrained.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandUnlockTrainingGameTests {
	private static final String SKILL = "crossfire_hurricane";

	private StandUnlockTrainingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void skippedProgressionAloneDoesNotFullyTrainALaterUnlock(GameTestHelper helper) {
		Player avdol = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(avdol), "fixture: could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			StandPower power = PowerClass.STAND.attachGet(avdol);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: Magician's Red not granted");
			helper.assertTrue(!StandPower.playerSkipsActionTraining(avdol), "fixture: the Survival user skips training");
			power.skipProgression();
			StandTypePersistentData data = power.getCurTypeData();
			helper.assertTrue(power.wasProgressionSkipped() && data.isSkillUnlocked(SKILL)
					&& power.getMoveset().getAbility(SKILL) instanceof TrainableAbility,
					"fixture: the skipped progression did not unlock the trainable " + SKILL);
			float max = ((TrainableAbility) power.getMoveset().getAbility(SKILL)).getMaxTrainingPoints(power);
			helper.assertTrue(max > 0 && data.getAbilityLearningProgressPoints(SKILL) == max,
					"fixture: the skipped progression did not fully train " + SKILL);

			// a skill the skipped progression did not cover (one added to the Stand later)
			data.setAbilityLearningProgressPoints(SKILL, 0.0F, max, power);
			data._setSkillUnlocked(SKILL, false, false);
			helper.assertTrue(!data.isSkillUnlocked(SKILL) && data.getAbilityLearningProgressPoints(SKILL) <= 0,
					"fixture: " + SKILL + " was not locked again");

			data.unlockOriginalResolveSkills(power);
			float points = data.getAbilityLearningProgressPoints(SKILL);
			helper.assertTrue(data.isSkillUnlocked(SKILL), "fixture: the maximum Resolve did not unlock " + SKILL + " again");
			helper.assertTrue(points == 0.0F,
					"1.16 unlockAction trains fully only while the user skips training, not after a skipped progression: "
							+ SKILL + " has " + points + " of " + max);
		}
		finally {
			avdol.discard();
		}
		helper.succeed();
	}
}

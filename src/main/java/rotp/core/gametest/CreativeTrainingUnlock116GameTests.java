package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandAction.canBeUnlocked: an action with noResolveUnlock (Hierophant Green's concentrated emerald splash, Crazy
 * Diamond's previous state, Magician's Red's Crossfire Hurricane special) was never unlocked by a Resolve level, only by
 * its training (onMaxTraining -> unlockAction), except that StandType.unlockNewActions unlocked every action, those
 * included, for a user in creative mode, for good.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeTrainingUnlock116GameTests {
	private CreativeTrainingUnlock116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void trainingUnlockedActionsUnlockForACreativeUserAsIn116(GameTestHelper helper) {
		unlocksForCreativeOnly(helper, "hierophant_green", "emerald_splash_concentrated");
		unlocksForCreativeOnly(helper, "crazy_diamond", "revert_state");
		unlocksForCreativeOnly(helper, "magicians_red", "crossfire_hurricane_special");

		StandUnlockableSkill plain = new StandUnlockableSkill("creative_unlock_test_plain");
		StandUnlockableSkill hidden = new StandUnlockableSkill("creative_unlock_test_hidden");
		hidden.setHidden();
		StandUnlockableSkill trained = new StandUnlockableSkill("creative_unlock_test_trained").setUnlockedByTraining();
		helper.assertTrue(StandTypePersistentData.isUnlockedByResolveOrCreative(plain, false)
				&& StandTypePersistentData.isUnlockedByResolveOrCreative(plain, true),
				"a skill that is not hidden is unlocked by the Resolve rule");
		helper.assertTrue(!StandTypePersistentData.isUnlockedByResolveOrCreative(hidden, true)
				&& !StandTypePersistentData.isUnlockedByResolveOrCreative(hidden, false),
				"a hidden skill that is not a training unlock (an add-on's own, as Bites the Dust) is not unlocked by the"
						+ " user's tick, creative or not");
		helper.assertTrue(StandTypePersistentData.isUnlockedByResolveOrCreative(trained, true)
				&& !StandTypePersistentData.isUnlockedByResolveOrCreative(trained, false),
				"a training unlock is unlocked by the user's tick only in creative mode");
		helper.succeed();
	}

	private static void unlocksForCreativeOnly(GameTestHelper helper, String standId, String skillName) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
		helper.assertTrue(standType != null, "Missing Stand type " + standId);
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CreativeUnlock"));
		try {
			user.getAbilities().instabuild = false;
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the " + standId + " user");
			StandPower power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			helper.assertTrue(!power.wasProgressionSkipped(), "setup: a survival grant skipped the progression of " + standId);
			power.setResolveLevel(4);
			StandTypePersistentData data = power.getCurTypeData();
			Ability ability = power.getAbility(skillName);
			helper.assertTrue(ability != null, "Missing ability " + skillName);

			data.unlockOriginalResolveSkills(power);
			helper.assertTrue(!data.isSkillUnlocked(skillName) && !ability.isAbilityUnlocked(power),
					"1.16 unlocked " + skillName + " by no Resolve level (noResolveUnlock): skill " + data.isSkillUnlocked(skillName)
							+ ", ability " + ability.isAbilityUnlocked(power));

			user.getAbilities().instabuild = true;
			data.unlockOriginalResolveSkills(power);
			helper.assertTrue(data.isSkillUnlocked(skillName) && ability.isAbilityUnlocked(power),
					"1.16 StandAction.canBeUnlocked unlocked " + skillName + " for a creative user: skill "
							+ data.isSkillUnlocked(skillName) + ", ability " + ability.isAbilityUnlocked(power));

			user.getAbilities().instabuild = false;
			helper.assertTrue(data.isSkillUnlocked(skillName) && ability.isAbilityUnlocked(power),
					"1.16 kept " + skillName + " unlocked once a creative user had it, back in survival: skill "
							+ data.isSkillUnlocked(skillName) + ", ability " + ability.isAbilityUnlocked(power));
		}
		finally {
			user.discard();
		}
	}
}

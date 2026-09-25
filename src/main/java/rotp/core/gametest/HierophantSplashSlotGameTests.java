package rotp.core.gametest;

import java.util.Map;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantEmeraldSplashAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 hierophant_green_es_concentrated was a SHIFT variation of the splash, and max training only unlocked it.
 * Under the owner boundary (Batch916) it is its own wheel slot: a trained user keeps the normal splash on LMB.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HierophantSplashSlotGameTests {
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("hierophant_splash_slot_gametest"));
	private static final String SPLASH = "emerald_splash";
	private static final String CONCENTRATED = "emerald_splash_concentrated";

	private HierophantSplashSlotGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void concentratedSplashHasItsOwnWheelSlot(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			Vec3 origin = helper.absoluteVec(new Vec3(0.5D, 1.0D, 0.5D));
			player.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Hierophant test player");
			StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
			helper.assertTrue(standType != null, "Missing Stand type hierophant_green");
			StandPower power = PowerClass.STAND.attachGet(player);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Hierophant Green: " + inserted.status());
			for (String skill : new String[] { SPLASH, CONCENTRATED }) {
				PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, skill);
				helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
						|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
						"Could not unlock " + skill + ": " + unlocked);
			}
			Ability splash = power.getMoveset().getAbility(SPLASH);
			Ability concentrated = power.getMoveset().getAbility(CONCENTRATED);
			helper.assertTrue(splash instanceof HierophantEmeraldSplashAbility
					&& concentrated instanceof HierophantEmeraldSplashAbility && splash != concentrated,
					"Hierophant Green lost its normal or concentrated splash");

			ControlSchemeTemplate template = power.getPowerType().makeDefaultControlSchemeTemplate();
			helper.assertTrue(countSlots(template, CONCENTRATED, InputMethod.HOLD) == 1,
					"The concentrated splash needs one held special wheel slot");
			helper.assertTrue(!hasModifierVariation(template),
					"The Hierophant wheel still carries a SHIFT/CTRL slot variation");

			// survival, untrained: the training gate hides the concentrated splash
			player.getAbilities().instabuild = false;
			power.getCurTypeData().setAbilityLearningProgressPoints(
					HierophantEmeraldSplashAbility.EMERALD_SPLASH_LEARNING_ABILITY, 0.0F,
					HierophantEmeraldSplashAbility.EMERALD_SPLASH_MAX_TRAINING, power);
			helper.assertTrue(!HierophantEmeraldSplashAbility.isEmeraldSplashFullyTrained(power),
					"The untrained fixture already counts as fully trained");
			helper.assertTrue(available(power, CONCENTRATED) == null,
					"The concentrated splash is usable before Emerald Splash is fully trained");

			// creative counts as fully trained: both splashes, each in its own slot
			player.getAbilities().instabuild = true;
			helper.assertTrue(HierophantEmeraldSplashAbility.isEmeraldSplashFullyTrained(power),
					"Creative no longer counts as fully trained");
			AvailableAbilities available = resolve(power);
			helper.assertTrue(available.getContextVariation(SPLASH) == splash,
					"A trained user's LMB splash was replaced by " + nameOf(available.getContextVariation(SPLASH)));
			helper.assertTrue(available.getContextVariation(CONCENTRATED) == concentrated,
					"The trained concentrated splash is missing from its own slot");
		}
		finally {
			player.discard();
		}
		helper.succeed();
	}

	private static AvailableAbilities resolve(StandPower power) {
		// a fresh resolution: the power caches its moves within a tick
		AvailableAbilities available = new AvailableAbilities();
		available.update(power, power.getMoveset());
		return available;
	}

	private static Ability available(StandPower power, String abilityName) {
		return resolve(power).getContextVariation(abilityName);
	}

	private static String nameOf(Ability ability) {
		return ability != null ? ability.name() : "nothing";
	}

	private static int countSlots(ControlSchemeTemplate template, String ability, InputMethod inputMethod) {
		int count = 0;
		for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
			for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
				for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
					Map<InputMethod, String> base = slot.get(null);
					if (base != null && ability.equals(base.get(inputMethod))) {
						count++;
					}
				}
			}
		}
		return count;
	}

	private static boolean hasModifierVariation(ControlSchemeTemplate template) {
		for (ControlSchemeTemplate.GroupTemplate group : template.groups.values()) {
			for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
				for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
					for (InputKey.Modifier modifier : slot.keySet()) {
						if (modifier != null) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}

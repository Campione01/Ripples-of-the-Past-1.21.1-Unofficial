package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonAction.afterClick (:92-97): every accepted Hamon technique except Hamon Breath (changesAuraColor,
 * HamonBreath:38) becomes HamonData.lastUsedAction, and getThisTickAuraColor (:1441-1457) colours the aura from it
 * only for Turquoise Blue, Sunlight Yellow, Scarlet and Metal Silver Overdrive. So any other technique used after
 * one of those gives the passive colour back, and the S.Y.O. Barrage is yellow only while it runs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonAuraLastTechniqueGameTests {
	private HamonAuraLastTechniqueGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void auraDropsATechniqueColourWhenAnotherTechniqueIsUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
		user.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(level.addFreshEntity(user), "Could not add the aura test player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		List<String> wrong = new ArrayList<>();
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.TURQUOISE_BLUE_OVERDRIVE.get());
			hamon.learnSkill(ModHamonSkills.ZOOM_PUNCH.get());
			hamon.learnSkill(ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get());
			hamon.learnSkill(ModHamonSkills.PROTECTION.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			helper.assertTrue(hamon.getEnergy() > 0.0F && user.getMainHandItem().isEmpty() && !user.isInWater(),
					"Fixture: a dry, unarmed user with energy, energy=" + hamon.getEnergy());
			check(wrong, "ORANGE", hamon, user, "before any technique");

			start(helper, component, power, user, "turquoise_blue_overdrive");
			check(wrong, "BLUE", hamon, user, "after Turquoise Blue Overdrive");

			start(helper, component, power, user, "hamon_breath");
			check(wrong, "BLUE", hamon, user, "after Turquoise Blue Overdrive and then Hamon Breath, which never changes the aura");

			start(helper, component, power, user, "zoom_punch");
			check(wrong, "ORANGE", hamon, user, "after Turquoise Blue Overdrive and then Zoom Punch, energy " + hamon.getEnergy());

			start(helper, component, power, user, "turquoise_blue_overdrive");
			check(wrong, "BLUE", hamon, user, "after Turquoise Blue Overdrive again");
			Ability protection = power.getAbility("hamon_protection");
			helper.assertTrue(protection != null, "Missing registered hamon_protection");
			protection.onClick(level, user, null);
			check(wrong, "ORANGE", hamon, user, "after Turquoise Blue Overdrive and then the Hamon Protection toggle");

			EntityActionInstance barrage = begin(helper, component, power, user, "sunlight_yellow_overdrive_barrage");
			helper.assertTrue(component.getAction() == barrage, "Fixture: the S.Y.O. Barrage is not the running action");
			check(wrong, "YELLOW", hamon, user, "while the Sunlight Yellow Overdrive Barrage runs");
			component.setAction(null, SyncType.NO_SYNC);
			check(wrong, "ORANGE", hamon, user, "after the Sunlight Yellow Overdrive Barrage ended, energy " + hamon.getEnergy());
			helper.assertTrue(wrong.isEmpty(), "1.16 aura colour " + String.join("; ", wrong));
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.discard();
		}
	}

	// the accepted click of a technique: its action is set on the user, then it is taken off again before it ticks
	private static void start(GameTestHelper helper, LivingComponentAction component, PlayerPower power, Player user, String name) {
		begin(helper, component, power, user, name);
		component.setAction(null, SyncType.NO_SYNC);
	}

	private static EntityActionInstance begin(GameTestHelper helper, LivingComponentAction component, PlayerPower power,
			Player user, String name) {
		Ability found = power.getAbility(name);
		helper.assertTrue(found instanceof EntityActionAbility, "Missing registered action technique " + name);
		EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(user.level(), user, user, null);
		helper.assertTrue(action != null, "Fixture: " + name + " made no action");
		component.setAction(action, user, SyncType.NO_SYNC);
		return action;
	}

	private static void check(List<String> wrong, String expected, HamonData hamon, Player user, String state) {
		String actual = hamon.auraColorNameThisTick(user);
		if (!expected.equals(actual)) {
			wrong.add(state + ": expected " + expected + ", got " + actual);
		}
	}
}

package rotp.core.gametest;

import java.util.Map;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16: the Dash Attack is Silver Chariot's heavy attack and the Sweeping Attack is only its
 * finisher variation (meter >= 0.5). RMB must hold the dash, never the sweep itself.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotFinisherBindingGameTests {
	private SilverChariotFinisherBindingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void rmbHoldsDashAndSweepOnlyAsMeterFinisher(GameTestHelper helper) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("silver_chariot"));
		helper.assertTrue(standType != null, "Missing Silver Chariot Stand type");
		MovesetBuilder defaults = standType.getDefaultMoveset();
		for (String schemeName : new String[] { "hotbar", "keybinds" }) {
			ControlSchemeTemplate scheme = defaults.controlSchemes.get(schemeName);
			helper.assertTrue(scheme != null, "Silver Chariot lost its " + schemeName + " scheme");
			helper.assertTrue("dash_attack".equals(clickRmbAbility(scheme)),
					schemeName + " scheme must bind RMB click to the dash heavy, got "
							+ clickRmbAbility(scheme));
			helper.assertTrue(!mentions(scheme, "sweeping_attack"),
					schemeName + " scheme binds the sweep finisher directly");
		}

		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
				UUID.fromString("0d8c61a4-5f5e-4c1b-9a0e-3b7f2a19c4d2"), "ChariotFinisher"));
		StandPower power = null;
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add Silver Chariot finisher player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
			power.setResolveLevel(power.getMaxResolveLevel());
			power.getCurTypeData()._setSkillUnlocked("sweeping_attack", true, false);
			Ability dash = power.getAbility("dash_attack");
			Ability sweep = power.getAbility("sweeping_attack");
			helper.assertTrue(dash != null && sweep != null && dash != sweep,
					"Dash and sweep are not distinct moveset abilities");
			helper.assertTrue(dash.isAbilityUnlocked(power) && sweep.isAbilityUnlocked(power),
					"Fixture did not unlock both the dash and the sweep");
			helper.assertTrue(standType.summon(user, power), "Could not summon Silver Chariot");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Silver Chariot is missing");
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina()); // the sweep needs stamina to be swapped in

			assertHeavyAt(helper, power, stand, 0.0F, dash);
			assertHeavyAt(helper, power, stand, 0.49F, dash);
			assertHeavyAt(helper, power, stand, 0.6F, sweep);
			assertHeavyAt(helper, power, stand, 0.0F, dash);
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void assertHeavyAt(GameTestHelper helper, StandPower power, StandEntity stand,
			float meter, Ability expected) {
		stand.setFinisherMeter(meter);
		power.tick(); // moves are cached per tick
		Ability resolved = power.updateAvailableMoves().getContextVariation("dash_attack");
		helper.assertTrue(resolved == expected, "At finisher meter " + meter
				+ " the RMB heavy resolved to " + (resolved != null ? resolved.name() : "nothing")
				+ " instead of " + expected.name());
	}

	private static String clickRmbAbility(ControlSchemeTemplate scheme) {
		for (ControlSchemeTemplate.GroupTemplate group : scheme.groups.values()) {
			for (Map.Entry<String, Pair<InputMethod, InputBindTemplate>> bind : group.separateBinds.entrySet()) {
				if (bind.getValue().getFirst() == InputMethod.CLICK && bind.getValue().getSecond() == InputKey.RMB) {
					return bind.getKey();
				}
			}
			for (ControlSchemeTemplate.SeparateBindTemplate bind : group.additionalSeparateBinds) {
				if (bind.inputMethod() == InputMethod.CLICK && bind.input() == InputKey.RMB) {
					return bind.ability();
				}
			}
		}
		return null;
	}

	private static boolean mentions(ControlSchemeTemplate scheme, String ability) {
		for (ControlSchemeTemplate.GroupTemplate group : scheme.groups.values()) {
			if (group.separateBinds.containsKey(ability)
					|| group.additionalSeparateBinds.stream().anyMatch(bind -> ability.equals(bind.ability()))) {
				return true;
			}
			for (ControlSchemeTemplate.AbilitiesHotbar hotbar : group.hotbars) {
				for (Map<InputKey.Modifier, Map<InputMethod, String>> slot : hotbar.slots) {
					for (Map<InputMethod, String> byMethod : slot.values()) {
						if (byMethod.containsValue(ability)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}

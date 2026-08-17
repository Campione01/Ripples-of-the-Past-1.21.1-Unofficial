package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.standpower.ArmoredStandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCRapierEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotState;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotEquipmentLifecycleGameTests {
	private SilverChariotEquipmentLifecycleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void freshSummonRestoresArmorAndLostRapier(
			GameTestHelper helper) {
		Player user = FakePlayerFactory.get(
				helper.getLevel(), new GameProfile(
						UUID.fromString("6ad5bb00-a65e-43fc-9815-9b5073dace03"),
						"SilverChariotEquipment"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("silver_chariot"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null,
					"Missing Silver Chariot Stand type");
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add Silver Chariot equipment test player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(
					power, new StandInstance(standType));
			helper.assertTrue(
					inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Silver Chariot: " + inserted.status());
			helper.assertTrue(standType.summon(user, power),
					"Could not summon Silver Chariot");

			StandEntity firstStand = power.getSummonedStandEntity();
			helper.assertTrue(firstStand != null,
					"First Silver Chariot entity is missing");
			SilverChariotState state = SilverChariotState.get(user);
			state.setHasArmor(false);
			state.setHasRapier(false);
			state.incrementTicksAfterArmorRemoval();
			state.setArmoredStats(new ArmoredStandStats(
					standType.getStandStats(), 0.0D, false));
			firstStand.refreshSilverChariotStateAfterMutation(user);
			helper.assertTrue(!firstStand.isSilverChariotArmorVisible()
					&& !firstStand.isSilverChariotRapierVisible(),
					"Silver Chariot equipment-loss fixture did not apply");

			SCRapierEntity lostRapier = new SCRapierEntity(
					firstStand, helper.getLevel());
			lostRapier.copyPosition(firstStand);
			helper.assertTrue(helper.getLevel().addFreshEntity(lostRapier),
					"Could not add the lost rapier fixture");
			standType.forceUnsummon(user, power);
			helper.assertTrue(!power.isSummoned() && firstStand.isRemoved(),
					"First Silver Chariot did not fully unsummon");
			lostRapier.tick();
			helper.assertTrue(lostRapier.isRemoved(),
					"Rapier owned by the old Stand survived its owner");

			helper.assertTrue(standType.summon(user, power),
					"Could not resummon Silver Chariot");
			StandEntity secondStand = power.getSummonedStandEntity();
			helper.assertTrue(secondStand != null && !secondStand.is(firstStand),
					"Resummon did not create a fresh Silver Chariot entity");
			helper.assertTrue(state.hasArmor() && state.hasRapier(),
					"Fresh summon did not restore armor and rapier state");
			helper.assertTrue(state.ticksAfterArmorRemoval() == 0,
					"Fresh summon retained the armor-removal timer");
			helper.assertTrue(state.armoredStats() == null,
					"Fresh summon retained stripped armor stats");
			helper.assertTrue(secondStand.isSilverChariotArmorVisible()
					&& secondStand.isSilverChariotRapierVisible(),
					"Fresh Silver Chariot did not publish restored equipment flags");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}

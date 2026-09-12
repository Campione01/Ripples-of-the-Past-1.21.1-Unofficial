package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.AvailableAbilities;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInput;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.SyncType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.ArmoredStandStats;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCRapierEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotState;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotEquipmentLifecycleGameTests {
	private SilverChariotEquipmentLifecycleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void rapierLaunchSelectsUnarmedLightAttackAndRecoveryRestoresIt(
			GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "ChariotUnarmed"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("silver_chariot"));
		StandPower power = null;
		SCRapierEntity rapier = null;
		try {
			helper.assertTrue(standType != null, "Missing Silver Chariot Stand type");
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add Silver Chariot unarmed-input player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
			Ability armed = power.getAbility("light_attack");
			Ability unarmed = power.getAbility("no_rapier_light_attack");
			helper.assertTrue(armed != null && unarmed != null && armed != unarmed,
					"Light-attack variants are not distinct registered moveset abilities");
			helper.assertTrue(armed.isAbilityUnlocked(power) && unarmed.isAbilityUnlocked(power),
					"The starting light-attack skill did not unlock both variants");
			helper.assertTrue(power.updateAvailableMoves().getContextVariation("light_attack") == armed,
					"An unsummoned armed Stand selected the unarmed variation");
			helper.assertTrue(standType.summon(user, power), "Could not summon Silver Chariot");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Silver Chariot is missing");
			LivingComponentAction actionComponent = LivingComponentAction.getComponent(stand);
			actionComponent.setAction(null, SyncType.NO_SYNC);
			assertLightAttackSelection(helper, power, armed);
			stand.setArmsOnlyMode(true);
			assertLightAttackSelection(helper, power, armed);
			stand.setArmsOnlyMode(false);

			power.setResolveLevel(power.getMaxResolveLevel());
			power.getCurTypeData()._setSkillUnlocked("rapier_launch", true, false);
			Ability launchAbility = power.getAbility("rapier_launch");
			helper.assertTrue(launchAbility instanceof EntityActionType,
					"Rapier launch is not an entity action");
			EntityActionInstance launch = ((EntityActionType) launchAbility)
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			actionComponent.setAction(launch, user, SyncType.NO_SYNC);
			launch.actionPerformStart();
			actionComponent.setAction(null, SyncType.NO_SYNC);
			rapier = helper.getLevel().getEntitiesOfClass(SCRapierEntity.class,
					user.getBoundingBox().inflate(4.0D), entity -> entity.getOwner() == stand)
					.stream().findFirst().orElse(null);
			helper.assertTrue(rapier != null, "Actual launch did not create a rapier projectile");
			helper.assertTrue(!SilverChariotState.get(user).hasRapier()
					&& !stand.isSilverChariotRapierVisible(),
					"Actual launch did not publish matching attachment and tracked equipment flags");
			assertLightAttackSelection(helper, power, unarmed);
			helper.assertTrue(AbilityInput.withConditionCheck(armed, user),
					"Base light-attack input rejected the available unarmed variation");

			// A stale visual flag must never overrule authoritative server equipment state.
			stand.setSilverChariotRapierVisible(true);
			assertLightAttackSelection(helper, power, unarmed);
			stand.refreshSilverChariotStateAfterMutation(user);
			rapier.takeRapier(user);
			stand.refreshSilverChariotStateAfterMutation(user);
			helper.assertTrue(SilverChariotState.get(user).hasRapier()
					&& stand.isSilverChariotRapierVisible(),
					"Rapier recovery did not restore both equipment representations");
			assertLightAttackSelection(helper, power, armed);
			helper.succeed();
		}
		finally {
			if (rapier != null && !rapier.isRemoved()) {
				rapier.discard();
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void assertLightAttackSelection(GameTestHelper helper, StandPower power, Ability expected) {
		AvailableAbilities available = power.updateAvailableMoves();
		helper.assertTrue(available.getContextVariation("light_attack") == expected,
				"Base light-attack input did not resolve to " + expected.name());
	}

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

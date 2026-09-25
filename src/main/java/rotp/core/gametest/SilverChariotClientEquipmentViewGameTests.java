package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.impl.stands.silverchariot.SilverChariotState;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 read the synced HAS_RAPIER / HAS_ARMOR entity data on both sides. The port keeps
 * the equipment in a server-only attachment, so client checks must read the Stand flags.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotClientEquipmentViewGameTests {
	private static final String RAPIER_WARNING = "jojo.message.action_condition.chariot_rapier";
	private static final String ARMOR_WARNING = "jojo.message.action_condition.chariot_armor";
	private static final String[] RAPIER_MOVES = { "rapier_launch", "dash_attack", "sweeping_attack", "melee_barrage" };

	private SilverChariotClientEquipmentViewGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void clientViewReadsStandFlagsAndMovesUseSharedCheck(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "ChariotClientView"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("silver_chariot"));
		StandPower power = null;
		try {
			helper.assertTrue(standType != null, "Missing Silver Chariot Stand type");
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add Silver Chariot client-view player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
			helper.assertTrue(standType.summon(user, power), "Could not summon Silver Chariot");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Silver Chariot is missing");
			SilverChariotState state = SilverChariotState.get(user);
			helper.assertTrue(state.hasRapier() && state.hasArmor(),
					"Fresh Silver Chariot did not start with rapier and armor");

			// The client attachment keeps its defaults; only the synced Stand flags carry the loss.
			stand.setSilverChariotRapierVisible(false);
			stand.setSilverChariotArmorVisible(true);
			helper.assertTrue(!SilverChariotState.readEquipment(power, null, true, true),
					"Client view kept a thrown rapier");
			helper.assertTrue(SilverChariotState.readEquipment(power, stand, false, true),
					"Client view lost armor that is still on");
			stand.setSilverChariotRapierVisible(true);
			stand.setSilverChariotArmorVisible(false);
			helper.assertTrue(SilverChariotState.readEquipment(power, null, true, true),
					"Client view lost a rapier that is still held");
			helper.assertTrue(!SilverChariotState.readEquipment(power, stand, false, true),
					"Client view kept removed armor");
			helper.assertTrue(SilverChariotState.hasRapier(power) && SilverChariotState.hasArmor(power, stand),
					"Server view let a stale Stand flag overrule the attachment");

			// Attachment lost both; stale true flags must not overrule it on the server.
			state.setHasRapier(false);
			state.setHasArmor(false);
			stand.setSilverChariotRapierVisible(true);
			stand.setSilverChariotArmorVisible(true);
			helper.assertTrue(!SilverChariotState.hasRapier(power) && !SilverChariotState.hasArmor(power, stand),
					"Server view read the Stand flags instead of the attachment");
			for (String move : RAPIER_MOVES) {
				assertWarning(helper, power, move, RAPIER_WARNING, true);
			}
			assertWarning(helper, power, "take_off_armor", ARMOR_WARNING, true);

			state.setHasRapier(true);
			state.setHasArmor(true);
			stand.refreshSilverChariotStateAfterMutation(user);
			for (String move : RAPIER_MOVES) {
				assertWarning(helper, power, move, RAPIER_WARNING, false);
			}
			assertWarning(helper, power, "take_off_armor", ARMOR_WARNING, false);

			// Client view through the moves: attachment keeps its defaults, the Stand flags carry the loss.
			stand.setSilverChariotRapierVisible(false);
			stand.setSilverChariotArmorVisible(false);
			assertClientView(helper, power, stand, true);
			// Stale attachment loss must not grey a move the Stand flags still show.
			state.setHasRapier(false);
			state.setHasArmor(false);
			stand.setSilverChariotRapierVisible(true);
			stand.setSilverChariotArmorVisible(true);
			assertClientView(helper, power, stand, false);
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	/** Runs every move check through the client branch of SilverChariotState. */
	private static void assertClientView(GameTestHelper helper, StandPower power, StandEntity stand,
			boolean lost) {
		SilverChariotState.withForcedClientView(() -> {
			helper.assertTrue(SilverChariotState.hasRapier(power) != lost
					&& SilverChariotState.hasArmor(power, stand) != lost,
					"Forced client view did not read the Stand flags");
			for (String move : RAPIER_MOVES) {
				assertWarning(helper, power, "client " + move, move, RAPIER_WARNING, lost);
			}
			assertWarning(helper, power, "client take_off_armor", "take_off_armor", ARMOR_WARNING, lost);
			return null;
		});
	}

	private static void assertWarning(GameTestHelper helper, StandPower power, String abilityName,
			String warningKey, boolean expected) {
		assertWarning(helper, power, abilityName, abilityName, warningKey, expected);
	}

	private static void assertWarning(GameTestHelper helper, StandPower power, String label,
			String abilityName, String warningKey, boolean expected) {
		Ability ability = power.getAbility(abilityName);
		helper.assertTrue(ability != null, "Missing Silver Chariot ability " + abilityName);
		ConditionCheck check = ability.checkSpecificConditions(power);
		Component warning = check.getWarning();
		boolean warned = !check.isPositive() && warning != null
				&& warning.getContents() instanceof TranslatableContents contents
				&& warningKey.equals(contents.getKey());
		helper.assertTrue(warned == expected,
				label + (expected ? " did not report " : " wrongly reported ") + warningKey);
	}
}

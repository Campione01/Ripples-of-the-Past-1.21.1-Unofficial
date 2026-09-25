package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.silverchariot.SilverChariotState;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotLeapGameTests {
	private SilverChariotLeapGameTests() {}

	// 1.16 SilverChariotEntity.leapBaseStrength: base attack damage, so losing the rapier keeps the leap.
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void noRapierKeepsStandLeapStrength(GameTestHelper helper) {
		Player user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), "ChariotLeap"));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(
				JojoMod.resLoc("silver_chariot"));
		StandPower power = null;
		SilverChariotState state = null;
		try {
			helper.assertTrue(standType != null, "Missing Silver Chariot Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 3, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user),
					"Could not add Silver Chariot leap player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Silver Chariot");
			power.setResolveLevel(power.getMaxResolveLevel());
			helper.assertTrue(standType.summon(user, power), "Could not summon Silver Chariot");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Silver Chariot is missing");
			state = SilverChariotState.get(user);
			helper.assertTrue(state != null, "Missing Silver Chariot equipment state");

			state.setHasRapier(true);
			stand.refreshSilverChariotStateAfterMutation(user);
			helper.assertTrue(power.isLeapUnlocked(), "Armed Silver Chariot did not unlock Stand leap");
			float armedLeap = power.leapStrength();
			helper.assertTrue(armedLeap >= 1.5F, "Armed Silver Chariot leap is too weak: " + armedLeap);

			state.setHasRapier(false);
			stand.refreshSilverChariotStateAfterMutation(user);
			// The no-rapier damage cut must actually be active for this check to mean anything.
			helper.assertTrue(stand.getAttributeValue(Attributes.ATTACK_DAMAGE)
					< stand.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) - 1.0E-3D,
					"No-rapier attack damage decrease was not applied");
			float unarmedLeap = power.leapStrength();
			helper.assertTrue(Math.abs(unarmedLeap - armedLeap) < 1.0E-4F,
					"Losing the rapier changed the leap: " + armedLeap + " -> " + unarmedLeap);
			helper.assertTrue(power.isLeapUnlocked(),
					"Silver Chariot without the rapier lost the Stand leap");
			helper.succeed();
		}
		finally {
			if (state != null) {
				state.setHasRapier(true);
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}

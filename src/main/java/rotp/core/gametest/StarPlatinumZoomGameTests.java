package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility;
import rotp.core.impl.stands.starplatinum.StarPlatinumZoomAbility.StarPlatinumZoom;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ended Star Platinum's zoom with its held action, also when the Stand went away without a release. The port
 * force-unsummons a Stand (portal, spectator mode, a lost power) with its action left in place, so the client's zoom
 * asks whether the Zoom still runs on a Stand that is still there.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarPlatinumZoomGameTests {
	private static final short KEY = 124;

	private StarPlatinumZoomGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void forceUnsummonedStandEndsTheZoom(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Star Platinum user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(2);
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());

			Ability zoom = power.getAbility("enhanced_eyesight");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(zoom);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
					"fixture: Zoom press not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, press, zoom.getAbilityId());
			helper.assertTrue(press.isActionSuccess && stand.getCurStandAction() instanceof StarPlatinumZoom,
					"fixture: Zoom did not start");
			helper.assertTrue(StarPlatinumZoomAbility.isZoomRunningOn(stand), "a held Zoom must count as running");

			type.forceUnsummon(user, power);
			helper.assertTrue(stand.isRemoved() && power.getSummonedStandEntity() == null,
					"fixture: the Stand was not force-unsummoned");
			helper.assertTrue(!StarPlatinumZoomAbility.isZoomRunningOn(stand),
					"a Zoom on a force-unsummoned Stand must not keep the camera zoomed in");
		}
		finally {
			AbilityInput.keyRelease(KEY, user);
			if (power != null && type != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
		helper.succeed();
	}
}

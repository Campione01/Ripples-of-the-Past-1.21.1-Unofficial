package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityAction.onClick summoned a Stand that was away before the press's checks, and afterClick finished that
 * summon with the checks' result (EntityStandType.finalizeStandSummonFromAction): a press its Stand's own condition
 * refused (StandEntityAction.checkStandConditions, an add-on's included) took the new Stand back, so the power was not
 * active after it. The scene presses an add-on-style Stand action whose condition refuses a Stand that is not in the
 * level yet, with Star Platinum away, as AbilityInput.keyPress calls it (Ability#onKeyPress). Own batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAwayRefusedPress116GameTests {
	private StandAwayRefusedPress116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "stand_away_refused_press_116")
	public static void aPressItsFreshStandRefusesTakesTheStandBackAsIn116(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = helper.absoluteVec(new Vec3(1.5D, 3.0D, 1.5D));
		user.setNoGravity(true);
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		RefusesAFreshStand ability = null;
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(!power.isSummoned(), "Fixture: Star Platinum is out before the press");
			ability = new AbilityType<RefusesAFreshStand>(JojoMod.resLoc("refuses_a_fresh_stand"), RefusesAFreshStand::new)
					.createInstance(new AbilityId(PowerClass.STAND, JojoMod.resLoc("star_platinum"), "refuses_a_fresh_stand"));
			BufferingState pressed = BufferingState.clickOnly();
			HeldInput held = ability.onKeyPress(helper.getLevel(), user, null, InputMethod.CLICK, 0.0F, pressed);
			StandEntity fresh = ability.asked;
			helper.assertTrue(fresh != null && !ability.askedWhileInTheLevel, "Fixture: the press did not summon a Stand and"
					+ " ask its condition of it before adding it: " + fresh);
			helper.assertTrue(held == null && !pressed.isActionSuccess, "Fixture: the refused press started " + held);
			helper.assertTrue(!power.isSummoned() && power.getSummonedStandEntity() == null && !fresh.isAddedToLevel()
					&& fresh.isRemoved(), "1.16's afterClick took back the Stand a press its Stand's condition refused had"
							+ " summoned, the power no longer active; the port's power has " + power.getSummonedStandEntity()
							+ " summoned, the Stand the press made in the level " + fresh.isAddedToLevel() + ", removed "
							+ fresh.isRemoved());
		}
		finally {
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			if (ability != null && ability.asked != null) {
				ability.asked.discard();
			}
			user.discard();
		}
		helper.succeed();
	}

	// an add-on Stand action whose Stand must already be in the level (as a condition on the Stand's state would be)
	private static final class RefusesAFreshStand extends StandEntityAbility {
		StandEntity asked;
		boolean askedWhileInTheLevel;

		RefusesAFreshStand(AbilityType<?> type, AbilityId id) {
			super(type, id, EntityActionInstance::new);
		}

		@Override
		protected ConditionCheck checkStandEntityConditions(StandPower standPower, StandEntity standEntity) {
			asked = standEntity;
			askedWhileInTheLevel = standEntity.isAddedToLevel();
			return askedWhileInTheLevel ? ConditionCheck.POSITIVE : ConditionCheck.createNegative("cant_control_stand");
		}
	}
}

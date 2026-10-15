package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.LivingComponentAction.TransactionSnapshot;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityHeavyAttack took the finisher meter's snapshot and spent 0.51 of it once, when the heavy attack was
 * set. A failed input transaction that puts the attack back (LivingComponentAction.rollbackFailedInputAction) runs its
 * onActionSet again; the snapshot, the recovery it gives and the meter stay as they were.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinisherRestoreGameTests {
	private FinisherRestoreGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "finisher_restore_1")
	public static void heavyPunchPutBackByAFailedInputDoesNotSpendTheFinisherAgain(GameTestHelper helper) {
		run(helper, "star_platinum", "heavy_punch");
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "finisher_restore_2")
	public static void silverChariotDashPutBackByAFailedInputDoesNotSpendTheFinisherAgain(GameTestHelper helper) {
		run(helper, "silver_chariot", "dash_attack");
	}

	private static void run(GameTestHelper helper, String standName, String abilityName) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
		StandPower power = null;
		LivingComponentAction component = null;
		try {
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Fixture: could not grant " + standName);
			// the finisher meter reads 0 until resolve level 1
			power.setResolveLevel(1);
			helper.assertTrue(type.summon(user, power), "Fixture: could not summon " + standName);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: the summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.setNoGravity(true);
			power.setStamina(power.getMaxStamina());
			component = LivingComponentAction.getComponent(stand);

			stand.setFinisherMeter(1.0F);
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: no " + abilityName + " action");
			EntityActionInstance heavy = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand,
					null);
			component.setAction(heavy, user, SyncType.NO_SYNC);
			float meter = stand.getFinisherMeter();
			float snapshot = stand.getLastHeavyFinisherValue();
			float recovery = heavy.phasesLength.getFloat(ActionPhase.RECOVERY);
			float finisherValue = heavy instanceof StandEntityHeavyPunch punch ? punch.finisherValue : snapshot;
			helper.assertTrue(stand.getCurStandAction() == heavy && Math.abs(meter - 0.49F) < 1.0E-4F
					&& Math.abs(snapshot - 1.0F) < 1.0E-4F, "Fixture: setting the " + abilityName + " left the meter at "
							+ meter + " and the snapshot at " + snapshot + ", expected 0.49 and 1");

			// an input that cleared the attack and then failed
			TransactionSnapshot before = component.captureTransactionSnapshot();
			component.setAction(null, user, SyncType.NO_SYNC);
			component.rollbackFailedInputAction(before);

			float recoveryAfter = heavy.phasesLength.getFloat(ActionPhase.RECOVERY);
			float finisherValueAfter = heavy instanceof StandEntityHeavyPunch punch ? punch.finisherValue
					: stand.getLastHeavyFinisherValue();
			helper.assertTrue(stand.getCurStandAction() == heavy, "Fixture: the " + abilityName + " was not put back: "
					+ stand.getCurStandAction());
			helper.assertTrue(stand.getFinisherMeter() == meter && stand.getLastHeavyFinisherValue() == snapshot
					&& recoveryAfter == recovery && finisherValueAfter == finisherValue,
					"The " + abilityName + " a failed input put back spent the finisher again: meter " + meter + " -> "
							+ stand.getFinisherMeter() + ", snapshot " + snapshot + " -> " + stand.getLastHeavyFinisherValue()
							+ ", recovery " + recovery + " -> " + recoveryAfter + "; 1.16 took and spent it once");
			helper.succeed();
		}
		finally {
			if (component != null) {
				component.setAction(null, user, SyncType.NO_SYNC);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}

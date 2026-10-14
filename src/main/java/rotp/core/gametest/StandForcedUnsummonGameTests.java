package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.StarInhaleAbility.InhaleAbilityInstance;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
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
 * A Stand taken away by force (a portal or another dimension, StandPower.tickWrongLevelStandEntity; its user losing
 * the power): 1.16 ended its user's held action through stopHeldAction, so the action ended as actions end, with its
 * cooldown. Here the Stand's action is cleared before the Stand is discarded, so its onActionCleared runs.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandForcedUnsummonGameTests {
	private static final short KEY = 158;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc("stand_forced_unsummon_gametest"));

	private StandForcedUnsummonGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void forcedUnsummonEndsAHeldInhaleWithItsCooldown(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			Ability inhale = scene.power.getAbility("inhale");
			var unlocked = UNLOCKS.forceUnlock(scene.power, "inhale");
			helper.assertTrue(inhale != null && (unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED), "fixture: Star Platinum's Inhale is missing");
			var selected = scene.power.updateAvailableMoves().getContextVariationContainer(inhale);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, scene.user, InputMethod.HOLD),
					"fixture: Inhale not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, scene.user, null, InputMethod.HOLD, 0, press, inhale.getAbilityId());
			EntityActionInstance action = scene.stand.getCurStandAction();
			helper.assertTrue(press.isActionSuccess && action instanceof InhaleAbilityInstance,
					"fixture: Inhale did not start: " + action);
			LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand);
			for (int tick = 0; tick < 20 && action.getFullTicksPassed() < 10; tick++) {
				actions.tick();
			}
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && scene.power.getAbilityCooldown("inhale") == 0,
					"fixture: Inhale is not held without a cooldown: " + action.getPhase() + " "
							+ scene.power.getAbilityCooldown("inhale"));
			// what StandPower does when its Stand is left in another dimension or the power can no longer be used
			scene.type.forceUnsummon(scene.user, scene.power);
			helper.assertTrue(scene.stand.isRemoved() && !scene.power.isSummoned(), "fixture: the Stand was not taken away");
			helper.assertTrue(scene.power.getAbilityCooldown("inhale") > 0,
					"1.16 stopHeldAction: a held Inhale ended by a forced unsummon starts its cooldown, got "
							+ scene.power.getAbilityCooldown("inhale"));
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void forcedUnsummonEndsTheActionOfAnArmsOnlyStandOnce(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.stand.setArmsOnlyMode();
			List<EntityActionInstance> clearedFor = new ArrayList<>();
			Ability punch = scene.power.getAbility("punch");
			helper.assertTrue(punch instanceof EntityActionType, "fixture: Star Platinum's punch is missing");
			EntityActionInstance probe = new EntityActionInstance((EntityActionType) punch) {
				@Override
				public void onActionCleared(@Nullable EntityActionInstance newAction) {
					clearedFor.add(newAction);
				}
			};
			probe.phasesLength.put(ActionPhase.PERFORM, 200F);
			probe.setStartingPhase();
			LivingComponentAction.getComponent(scene.stand).setAction(probe, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(scene.stand.getCurStandAction() == probe && scene.stand.isArmsOnlyMode(),
					"fixture: the arms-only Stand is not running the probe action");
			scene.type.forceUnsummon(scene.user, scene.power);
			helper.assertTrue(clearedFor.size() == 1,
					"a forced unsummon ends the Stand's action once, got " + clearedFor.size() + " ends");
			helper.assertTrue(scene.stand.isRemoved() && scene.stand.getCurStandAction() == null,
					"the arms-only Stand was left with " + scene.stand.getCurStandAction() + ", removed=" + scene.stand.isRemoved());
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	private static final class Scene implements AutoCloseable {
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		boolean closed;

		Scene(GameTestHelper helper) {
			Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(start.x, start.y, start.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "fixture: user not added");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "fixture: Star Platinum not granted");
			power.setResolveLevel(2);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(type.summon(user, power), "fixture: Star Platinum not summoned");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isAddedToLevel(), "fixture: no summoned Stand");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			if (!stand.isRemoved()) {
				stand.discard();
			}
			user.discard();
		}
	}
}

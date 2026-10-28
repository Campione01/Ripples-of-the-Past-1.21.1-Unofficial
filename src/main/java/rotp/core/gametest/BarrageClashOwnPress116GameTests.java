package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 StandEntityAction.checkConditions -> checkTaskCancelling -> canClickDuringTask: a barrage pressed again while it was
 * its Stand's task was refused, with no message, during a barrage clash too (StandEntityMeleeBarrage was not chainable),
 * for a player's click and for a Stand-bearing mob's (its StandAI picked through checkConditions), and the client sent no
 * press of a held action while the power held one (ActionsOverlayGui.onActionClick). So no press set the same barrage again
 * while its clash held, and StandEntityTask.makeServerSideTask never built such a task at the clash opponent.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BarrageClashOwnPress116GameTests {

	private BarrageClashOwnPress116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theSameBarragePressedAgainDuringItsClashIsRefusedAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			StandPower rivalPower = null;
			LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
			try {
				Vec3 rivalPos = scene.user().position().add(0.0D, 0.0D, 4.0D);
				rival.setNoGravity(true);
				rival.moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
				helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
				rivalPower = PowerClass.STAND.attachGet(rival);
				helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED && type.summon(rival, rivalPower),
						"Could not give the rival Star Platinum");
				StandEntity opponent = rivalPower.getSummonedStandEntity();
				Vec3 opponentPos = scene.user().position().add(0.0D, 0.0D, 2.0D);
				opponent.setNoGravity(true);
				opponent.moveTo(opponentPos.x, opponentPos.y, opponentPos.z, 180.0F, 0.0F);
				Ability barrage = scene.power().getAbility("barrage");
				helper.assertTrue(barrage != null, "Missing Star Platinum's barrage");

				EntityActionInstance running = scene.start("barrage", ActionPhase.PERFORM);
				// 1.16 refused it with no clash as well (canClickDuringTask in every phase), where the port's barrage holds
				// every other press back
				AbilityConditionCheck free = resolve(scene.power(), barrage);
				helper.assertTrue(free != null && free.ability == barrage && !free.conditionCheck.isPositive()
						&& free.conditionCheck.getWarning() == null && !running.canBeCancelledInto(running.ability),
						"1.16 refused the barrage pressed again in its own PERFORM with no clash, silently: " + describe(free));

				setBarrageClashOpponent(scene.stand(), opponent);
				setBarrageClashOpponent(opponent, scene.stand());
				helper.assertTrue(scene.stand().barrageClashOpponent().orElse(null) == opponent
						&& opponent.barrageClashOpponent().orElse(null) == scene.stand(), "setup: the two Stands are not in a barrage clash");
				refused(helper, scene, barrage, running, "in its PERFORM");
				running.startRecovery();
				helper.assertTrue(running.getPhase() == ActionPhase.RECOVERY && !running.isOver()
						&& scene.stand().barrageClashOpponent().orElse(null) == opponent, "setup: the barrage is not in its RECOVERY"
								+ " with its clash held: phase " + running.getPhase());
				refused(helper, scene, barrage, running, "in its RECOVERY");
			}
			finally {
				actions.setAction(null, scene.user(), SyncType.NO_SYNC);
				scene.stand().barrageClashStopped();
				if (rivalPower != null && rivalPower.isSummoned()) {
					type.forceUnsummon(rival, rivalPower);
				}
				rival.discard();
			}
		});
		helper.succeed();
	}

	// the player's key (the resolved container) and the pool a Stand-bearing mob picks from (inMovesetAndCanBeUsed)
	private static void refused(GameTestHelper helper, Scene scene, Ability barrage, EntityActionInstance running, String when) {
		AbilityConditionCheck key = resolve(scene.power(), barrage);
		AvailableAbilities moves = scene.power().updateAvailableMoves();
		helper.assertTrue(key != null && key.ability == barrage && !key.conditionCheck.isPositive()
				&& key.conditionCheck.getWarning() == null && !moves.inMovesetAndCanBeUsed.containsValue(barrage)
				&& scene.stand().getCurStandAction() == running, "1.16 refused the barrage pressed again " + when + " during its"
						+ " barrage clash, silently, for a player and a Stand-bearing mob (not chainable): the key resolves to "
						+ describe(key) + ", usable for a mob " + moves.inMovesetAndCanBeUsed.containsValue(barrage));
	}

	// what a Stand's parry of the opponent's barrage does (StandEntity.barrageClashParryPunches)
	private static void setBarrageClashOpponent(StandEntity stand, Entity opponent) {
		try {
			Method clash = StandEntity.class.getDeclaredMethod("setBarrageClashOpponent", Entity.class);
			clash.setAccessible(true);
			clash.invoke(stand, opponent);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: could not set the barrage clash opponent", e);
		}
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(StandPower power, Ability base) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(base);
	}

	private static String describe(AbilityConditionCheck container) {
		return container == null ? "nothing" : container.ability.name() + " (" + (container.conditionCheck.isPositive()
				? "usable" : "refused") + ")";
	}
}

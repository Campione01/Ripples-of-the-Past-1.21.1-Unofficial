package rotp.core.powersystem.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 PowerBaseImpl.tickHeldAction re-checked a held action on every tick and stopHeldAction(false) ended a failed
 * hold without firing it; StandEntityMeleeBarrage.stopOnHeavyAttack let a heavy attack stop the barrage; and
 * Action.onPerform reset a player's attack strength for a swingHand action. The checks that need a world are in
 * rotp.core.gametest.HeldConditionRecheckGameTests.
 */
public final class HeldConditionRecheckSmokeTest {
	private HeldConditionRecheckSmokeTest() {}

	public static void run() {
		verifyFailedCheckStop();
		verifyNoUserNoCheck();
		verifyHeavyAttackStop();
		verifySwingHandFlag();
	}

	private static void verifyFailedCheckStop() {
		EntityActionAbility ability = entityAbility("failed_check");
		ReleaseRecorder charging = inPhase(new ReleaseRecorder(ability), ActionPhase.BUTTON_CHARGE);
		ability.stopHeldActionOnFailedCheck(charging);
		check(charging.isOver() && !charging.released,
				"a charge that fails its check must be dropped, not released into firing");

		ReleaseRecorder holding = inPhase(new ReleaseRecorder(ability), ActionPhase.PERFORM);
		ability.stopHeldActionOnFailedCheck(holding);
		check(holding.released && holding.getPhase() == ActionPhase.RECOVERY,
				"a hold that fails its check must end the way releasing the key ends it");
	}

	private static void verifyNoUserNoCheck() {
		EntityActionAbility ability = entityAbility("no_user");
		ability.setButtonHoldPhase(ActionPhase.PERFORM);
		EntityActionInstance holding = inPhase(new EntityActionInstance(ability), ActionPhase.PERFORM);
		check(ability.isActionHeld(holding), "the test hold is not held");
		check(!ability.stopHeldActionIfConditionsFail(holding) && holding.getPhase() == ActionPhase.PERFORM,
				"a hold without a user or a held key must not be checked");
		check(ability.canFireReleasedHold(holding),
				"a release the server cannot check must fire as the client predicted");
	}

	private static void verifyHeavyAttackStop() {
		EntityActionAbility plain = entityAbility("heavy_plain");
		check(!plain.stopsOnHeavyAttack(inPhase(new EntityActionInstance(plain), ActionPhase.PERFORM)),
				"only an action that opts in stops on a heavy attack");
		StandEntityBarrageAbility barrage = new AbilityType<StandEntityBarrageAbility>(id("heavy_barrage"),
				StandEntityBarrageAbility::new).createInstance(abilityId("barrage"));
		check(barrage.stopsOnHeavyAttack(inPhase(new StandEntityBarrageAbility.StandEntityBarrage(barrage), ActionPhase.PERFORM)),
				"1.16 StandEntityMeleeBarrage.stopOnHeavyAttack: a heavy attack stops the barrage");
		EntityActionAbility.onHitByHeavyAttack(null);
	}

	private static void verifySwingHandFlag() {
		EntityActionAbility ability = entityAbility("swing_hand");
		check(!ability.resetsAttackStrengthOnPerform(), "an action must not reset the attack strength unless it swings the hand");
		check(ability.setResetsAttackStrengthOnPerform() == ability && ability.resetsAttackStrengthOnPerform(),
				"setResetsAttackStrengthOnPerform must mark the action");
	}

	private static final class ReleaseRecorder extends EntityActionInstance {
		private boolean released;

		private ReleaseRecorder(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onButtonStopHold() {
			released = true;
			startRecovery();
		}
	}

	private static <T extends EntityActionInstance> T inPhase(T action, ActionPhase phase) {
		for (ActionPhase each : ActionPhase.values()) {
			action.phasesLength.put(each, 10.0F);
		}
		action.setPhaseStart(phase);
		check(action.getPhase() == phase, "could not put the test action in " + phase);
		return action;
	}

	private static EntityActionAbility entityAbility(String name) {
		return new AbilityType<EntityActionAbility>(id("held_recheck_" + name),
				(type, abilityId) -> new EntityActionAbility(type, abilityId, EntityActionInstance::new))
				.createInstance(abilityId(name));
	}

	private static AbilityId abilityId(String name) {
		return new AbilityId(null, id("test_power"), name);
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("jojo_ripples", path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

package rotp.core.powersystem.ability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rotp.core.impl.powers.hamon.abilities.HamonHypnosisAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieDevourAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;

/**
 * 1.16 Action.cancelHeldOnGettingAttacked: a hit stops a held action only when the action says so, only while it
 * is held, and a charge that has not fired is dropped rather than fired. The barrage counts only a linked hit of
 * 4 or more; Hypnosis is held while it charges.
 */
public final class HeldActionAttackedCancelSmokeTest {
	private HeldActionAttackedCancelSmokeTest() {}

	public static void run() {
		verifyOptIn();
		verifyHeldPhases();
		verifyStop();
		verifyCoreAbilities();
	}

	private static void verifyOptIn() {
		EntityActionAbility plain = new AbilityType<EntityActionAbility>(id("jojo_ripples", "held_cancel_test"),
				(type, abilityId) -> new EntityActionAbility(type, abilityId, EntityActionInstance::new))
				.createInstance(abilityId("plain"));
		EntityActionInstance action = inPhase(new EntityActionInstance(plain), ActionPhase.BUTTON_CHARGE);
		check(!plain.cancelHeldOnGettingAttacked(action, null, 20.0F),
				"an action must not stop on a hit unless it opts in");
		check(plain.setCancelHeldOnGettingAttacked() == plain
				&& plain.cancelHeldOnGettingAttacked(action, null, 0.0F),
				"setCancelHeldOnGettingAttacked must make any hit count");
	}

	private static void verifyHeldPhases() {
		EntityActionAbility charged = entityAbility("charged");
		check(charged.isActionHeld(inPhase(new EntityActionInstance(charged), ActionPhase.BUTTON_CHARGE)),
				"a button charge is held");
		check(!charged.isActionHeld(inPhase(new EntityActionInstance(charged), ActionPhase.PERFORM))
				&& !charged.isActionHeld(inPhase(new EntityActionInstance(charged), ActionPhase.RECOVERY)),
				"a fired action is no longer held");
		EntityActionInstance over = inPhase(new EntityActionInstance(charged), ActionPhase.BUTTON_CHARGE);
		over.forceStop();
		check(!charged.isActionHeld(over), "a stopped action is not held");

		EntityActionAbility holding = entityAbility("holding");
		holding.setButtonHoldPhase(ActionPhase.PERFORM);
		check(holding.isActionHeld(inPhase(new EntityActionInstance(holding), ActionPhase.PERFORM)),
				"the button-holding phase is held");
		check(!holding.isActionHeld(inPhase(new EntityActionInstance(holding), ActionPhase.WINDUP)),
				"a windup before the holding phase is not held");
	}

	private static void verifyStop() {
		EntityActionAbility ability = entityAbility("stop");
		ReleaseRecorder charging = inPhase(new ReleaseRecorder(ability), ActionPhase.BUTTON_CHARGE);
		ability.stopHeldActionOnGettingAttacked(charging);
		check(charging.isOver() && !charging.released,
				"a charge that has not fired must be dropped, not released into firing");

		ReleaseRecorder holding = inPhase(new ReleaseRecorder(ability), ActionPhase.PERFORM);
		ability.stopHeldActionOnGettingAttacked(holding);
		check(holding.released && holding.getPhase() == ActionPhase.RECOVERY,
				"a hold must end the way releasing the key ends it");
	}

	private static void verifyCoreAbilities() {
		StandEntityBarrageAbility barrage = new AbilityType<StandEntityBarrageAbility>(id("jojo_ripples", "barrage"),
				StandEntityBarrageAbility::new).createInstance(abilityId("barrage"));
		EntityActionInstance barraging = inPhase(new EntityActionInstance(barrage), ActionPhase.PERFORM);
		DamageSource linked = allocate(StandLinkDamageSource.class);
		DamageSource direct = allocate(DamageSource.class);
		check(barrage.cancelHeldOnGettingAttacked(barraging, linked, 4.0F),
				"a linked hit of 4 must stop the barrage");
		check(!barrage.cancelHeldOnGettingAttacked(barraging, linked, 3.99F),
				"a linked hit below 4 must not stop the barrage");
		check(!barrage.cancelHeldOnGettingAttacked(barraging, direct, 20.0F),
				"a hit on the user itself must not stop the barrage");
		check(barrage.isActionHeld(inPhase(new EntityActionInstance(barrage), ActionPhase.WINDUP))
				&& barrage.isActionHeld(barraging)
				&& !barrage.isActionHeld(inPhase(new EntityActionInstance(barrage), ActionPhase.RECOVERY)),
				"the barrage is held until its recovery");

		TimeStopAbility timeStop = new AbilityType<TimeStopAbility>(id("jojo_ripples", "time_stop"),
				TimeStopAbility::new).createInstance(abilityId("time_stop"));
		check(!timeStop.cancelHeldOnGettingAttacked(inPhase(new EntityActionInstance(timeStop), ActionPhase.BUTTON_CHARGE),
				direct, 20.0F),
				"a time stop cancels on a hit only where its moveset opts in (The World, not Star Platinum)");

		HamonHypnosisAbility hypnosis = new AbilityType<HamonHypnosisAbility>(id("jojo_ripples", "hypnosis"),
				HamonHypnosisAbility::new).createInstance(abilityId("hypnosis"));
		hypnosis.hamonHoldToFire(60, false, 60, 6);
		check(hypnosis.cancelHeldOnGettingAttacked(inPhase(new EntityActionInstance(hypnosis), ActionPhase.WINDUP),
				direct, 1.0F),
				"Hypnosis must stop on any hit");
		check(hypnosis.isActionHeld(inPhase(new EntityActionInstance(hypnosis), ActionPhase.WINDUP))
				&& !hypnosis.isActionHeld(inPhase(new EntityActionInstance(hypnosis), ActionPhase.PERFORM)),
				"Hypnosis is held while it charges, not after it fires");

		VampirismBloodDrainAbility drain = new AbilityType<VampirismBloodDrainAbility>(id("jojo_ripples", "blood_drain"),
				VampirismBloodDrainAbility::new).createInstance(abilityId("blood_drain"));
		ZombieDevourAbility devour = new AbilityType<ZombieDevourAbility>(id("jojo_ripples", "devour"),
				ZombieDevourAbility::new).createInstance(abilityId("devour"));
		for (EntityActionAbility held : new EntityActionAbility[] { drain, devour }) {
			EntityActionInstance holding = inPhase(new EntityActionInstance(held), ActionPhase.PERFORM);
			check(held.isActionHeld(holding) && held.cancelHeldOnGettingAttacked(holding, direct, 1.0F),
					held.getAbilityId().nameInMoveset() + " must stop on any hit while held");
		}
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
		return new AbilityType<EntityActionAbility>(id("jojo_ripples", "held_cancel_" + name),
				(type, abilityId) -> new EntityActionAbility(type, abilityId, EntityActionInstance::new))
				.createInstance(abilityId(name));
	}

	// A damage source without a level: the checks read only its class.
	private static <T> T allocate(Class<T> type) {
		try {
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field singleton = unsafeClass.getDeclaredField("theUnsafe");
			singleton.setAccessible(true);
			Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
			return type.cast(allocateInstance.invoke(singleton.get(null), type));
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("could not allocate " + type.getSimpleName(), e);
		}
	}

	private static AbilityId abilityId(String name) {
		return new AbilityId(null, id("jojo_ripples", "test_power"), name);
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

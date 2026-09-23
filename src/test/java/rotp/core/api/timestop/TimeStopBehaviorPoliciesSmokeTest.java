package rotp.core.api.timestop;

import net.minecraft.resources.ResourceLocation;

public final class TimeStopBehaviorPoliciesSmokeTest {
	private TimeStopBehaviorPoliciesSmokeTest() {}

	public static void main(String[] args) {
		TimeStopBehaviorPolicies.resetForTests();
		ResourceLocation owner = id("owner");
		ResourceLocation target = id("target");
		TimeStopProgressionPolicy progression =
				new TimeStopProgressionPolicy(
						200, 500, (float) Math.PI, Float.MAX_VALUE, -1.0F);
		TimeStopBehaviorPolicy policy = new TimeStopBehaviorPolicy() {
			@Override
			public TimeStopProgressionPolicy progression() {
				return progression;
			}
		};

		TimeStopBehaviorPolicies.register(owner, target, policy);
		check(TimeStopBehaviorPolicies.registeredOwners().equals(
				java.util.List.of(owner)), "owner binding was not retained");
		check(TimeStopBehaviorPolicies.registeredStandTypes().equals(
				java.util.List.of(target)), "Stand binding was not retained");
		check(TimeStopBehaviorPolicies.find(target) == policy,
				"target did not resolve its exact policy");
		check(TimeStopBehaviorPolicies.find(id("missing")) == null,
				"an unbound Stand resolved a policy");

		expectFailure(
				() -> TimeStopBehaviorPolicies.register(
						owner, id("other_target"), new TimeStopBehaviorPolicy() {}),
				"duplicate owner was accepted");
		expectFailure(
				() -> TimeStopBehaviorPolicies.register(
						id("other_owner"), target, new TimeStopBehaviorPolicy() {}),
				"duplicate Stand target was accepted");

		check(TimeStopStartupCostDecision.pass().resolve(225.0F) == 225.0F,
				"PASS changed the default startup cost");
		check(TimeStopStartupCostDecision.override(1.0F).resolve(225.0F)
						== 1.0F,
				"OVERRIDE did not replace the startup cost");
		check(TimeStopStartupCostDecision.deny().isDenied(),
				"DENY did not reject startup");
		expectFailure(
				() -> TimeStopStartupCostDecision.override(Float.NaN),
				"non-finite startup cost was accepted");
		expectFailure(
				() -> TimeStopStartupCostDecision.override(-1.0F),
				"negative startup cost was accepted");

		check(TimeStopAudioDecision.pass().kind()
						== TimeStopAudioDecision.Kind.PASS,
				"audio PASS kind drifted");
		check(TimeStopAudioDecision.silent().kind()
						== TimeStopAudioDecision.Kind.SILENT,
				"audio SILENT kind drifted");
		check(progression.humanMaxTicks() == 200
						&& progression.enhancedMaxTicks() == 500
						&& progression.zombieMaxTicks() == 500
						&& progression.learningPerTick() == (float) Math.PI
						&& progression.decayPerDay() == Float.MAX_VALUE
						&& progression.cooldownPerTick() == -1.0F,
				"progression values drifted");
		expectFailure(
				() -> new TimeStopProgressionPolicy(
						200, 199, 1.0F, 0.0F, 3.0F),
				"decreasing enhanced maximum was accepted");
		checkZombieMaximum();

		TimeStopBehaviorPolicies.resetForTests();
		check(TimeStopBehaviorPolicies.registeredOwners().isEmpty()
						&& TimeStopBehaviorPolicies.registeredStandTypes()
								.isEmpty(),
				"test reset retained time-stop behavior bindings");
	}

	/**
	 * 1.16 TimeStop.Builder.timeStopMaxTicks(forHuman, forVampire, forPillarman, forZombie) kept
	 * zombies on their own cap (at least the human one); the two-value form gave them the vampire cap.
	 */
	private static void checkZombieMaximum() {
		TimeStopProgressionPolicy shadow =
				new TimeStopProgressionPolicy(60, 80, 70, 0.1F, 0.0F, 3.0F);
		check(shadow.humanMaxTicks() == 60
						&& shadow.enhancedMaxTicks() == 80
						&& shadow.zombieMaxTicks() == 70
						&& shadow.learningPerTick() == 0.1F
						&& shadow.decayPerDay() == 0.0F
						&& shadow.cooldownPerTick() == 3.0F,
				"six-value progression values drifted");
		check(new TimeStopProgressionPolicy(60, 80, 0.1F, 0.0F, 3.0F)
						.equals(new TimeStopProgressionPolicy(
								60, 80, 80, 0.1F, 0.0F, 3.0F)),
				"five-value progression must give zombies the enhanced maximum");
		check(new TimeStopProgressionPolicy(60, 80, 100, 0.1F, 0.0F, 3.0F)
						.zombieMaxTicks() == 100
						&& new TimeStopProgressionPolicy(
								60, 80, 60, 0.1F, 0.0F, 3.0F)
								.zombieMaxTicks() == 60,
				"a zombie maximum from the human value up was rejected");
		expectFailure(
				() -> new TimeStopProgressionPolicy(
						60, 80, 59, 0.1F, 0.0F, 3.0F),
				"zombie maximum below the human maximum was accepted");
		try {
			// add-ons compiled against the five-value record call this constructor
			TimeStopProgressionPolicy.class.getConstructor(
					int.class, int.class, float.class, float.class, float.class);
		}
		catch (NoSuchMethodException error) {
			throw new AssertionError(
					"five-value progression constructor was removed", error);
		}
	}

	private static void expectFailure(Runnable action, String message) {
		try {
			action.run();
		}
		catch (IllegalArgumentException | IllegalStateException expected) {
			return;
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath("rotp_test", path);
	}
}

package com.github.standobyte.jojo.api.timestop;

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
						&& progression.learningPerTick() == (float) Math.PI
						&& progression.decayPerDay() == Float.MAX_VALUE
						&& progression.cooldownPerTick() == -1.0F,
				"progression values drifted");
		expectFailure(
				() -> new TimeStopProgressionPolicy(
						200, 199, 1.0F, 0.0F, 3.0F),
				"decreasing enhanced maximum was accepted");

		TimeStopBehaviorPolicies.resetForTests();
		check(TimeStopBehaviorPolicies.registeredOwners().isEmpty()
						&& TimeStopBehaviorPolicies.registeredStandTypes()
								.isEmpty(),
				"test reset retained time-stop behavior bindings");
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

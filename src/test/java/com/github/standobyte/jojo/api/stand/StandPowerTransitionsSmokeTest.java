package com.github.standobyte.jojo.api.stand;

import java.util.Optional;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions.PowerAccess;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Result;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Status;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.TestStandInstances;

import net.minecraft.resources.ResourceLocation;

public final class StandPowerTransitionsSmokeTest {
	private static final ResourceLocation ALPHA_SKIN =
			id("jojo_ripples", "test_alpha_skin");
	private static final ResourceLocation CHANGED_SKIN =
			id("jojo_ripples", "test_changed_skin");

	private StandPowerTransitionsSmokeTest() {}

	public static void run() {
		ResourceLocation alphaId = id("jojo_ripples", "test_transition_alpha");
		ResourceLocation betaId = id("jojo_ripples", "test_transition_beta");
		ResourceLocation disabledId = id("jojo_ripples", "test_transition_disabled");

		StandInstance alphaInstance = TestStandInstances.valid(alphaId);
		alphaInstance.removePart(StandPart.ARMS);
		alphaInstance.setCustomSkin(Optional.of(ALPHA_SKIN));
		StandInstance betaInstance = TestStandInstances.valid(betaId);

		FakePower clientPower = new FakePower(true, Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(clientPower, alphaInstance),
				Status.NOT_SERVER_SIDE,
				clientPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(clientPower, null),
				Status.NOT_SERVER_SIDE,
				clientPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.extract(clientPower, null),
				Status.NOT_SERVER_SIDE,
				clientPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.replace(clientPower, null, null),
				Status.NOT_SERVER_SIDE,
				clientPower,
				0,
				Optional.empty());

		FakePower emptyPower = new FakePower(false, Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(emptyPower, null),
				Status.INVALID_REPLACEMENT,
				emptyPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(emptyPower, TestStandInstances.invalid(disabledId)),
				Status.INVALID_REPLACEMENT,
				emptyPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.extract(emptyPower, alphaId),
				Status.NO_STAND,
				emptyPower,
				0,
				Optional.empty());
		assertFailure(
				StandPowerTransitions.replace(emptyPower, alphaId, betaInstance),
				Status.NO_STAND,
				emptyPower,
				0,
				Optional.empty());

		Result inserted = StandPowerTransitions.insert(emptyPower, alphaInstance);
		check(inserted.status() == Status.APPLIED, "insert must apply");
		check(inserted.applied(), "applied status helper must be true");
		check(inserted.previous().isEmpty(), "insert must not report a previous Stand");
		check(inserted.current().orElseThrow().equals(alphaInstance),
				"insert must report the committed Stand");
		check(emptyPower.writes == 1, "insert must perform exactly one write");

		alphaInstance.addPart(StandPart.ARMS);
		alphaInstance.setCustomSkin(Optional.of(CHANGED_SKIN));
		StandInstance storedAfterInputMutation = emptyPower.current.orElseThrow();
		check(!storedAfterInputMutation.hasPart(StandPart.ARMS),
				"mutating the insert input must not change stored state");
		check(storedAfterInputMutation.getSelectedSkin().equals(Optional.of(ALPHA_SKIN)),
				"mutating the insert input skin must not change stored state");

		StandInstance resultSnapshot = inserted.current().orElseThrow();
		resultSnapshot.addPart(StandPart.ARMS);
		resultSnapshot.setCustomSkin(Optional.of(CHANGED_SKIN));
		check(!inserted.current().orElseThrow().hasPart(StandPart.ARMS),
				"Result accessors must return defensive copies");
		check(!emptyPower.current.orElseThrow().hasPart(StandPart.ARMS),
				"mutating a Result snapshot must not change stored state");

		assertFailure(
				StandPowerTransitions.insert(emptyPower, betaInstance),
				Status.STAND_ALREADY_PRESENT,
				emptyPower,
				1,
				Optional.of(storedAfterInputMutation));
		assertFailure(
				StandPowerTransitions.extract(emptyPower, betaId),
				Status.SOURCE_MISMATCH,
				emptyPower,
				1,
				Optional.of(storedAfterInputMutation));
		assertFailure(
				StandPowerTransitions.replace(emptyPower, betaId, betaInstance),
				Status.SOURCE_MISMATCH,
				emptyPower,
				1,
				Optional.of(storedAfterInputMutation));
		assertFailure(
				StandPowerTransitions.replace(emptyPower, alphaId, null),
				Status.INVALID_REPLACEMENT,
				emptyPower,
				1,
				Optional.of(storedAfterInputMutation));

		Result replaced = StandPowerTransitions.replace(emptyPower, alphaId, betaInstance);
		check(replaced.status() == Status.APPLIED, "replace must apply for the expected source");
		check(replaced.previous().orElseThrow().equals(storedAfterInputMutation),
				"replace must report the previous Stand");
		check(replaced.current().orElseThrow().equals(betaInstance),
				"replace must report the replacement Stand");
		check(emptyPower.current.orElseThrow().equals(betaInstance),
				"replace must commit the replacement Stand");
		check(emptyPower.writes == 2, "replace must perform exactly one write");

		betaInstance.removePart(StandPart.LEGS);
		check(emptyPower.current.orElseThrow().hasPart(StandPart.LEGS),
				"mutating the replace input must not change stored state");

		Result extracted = StandPowerTransitions.extract(emptyPower, betaId);
		check(extracted.status() == Status.APPLIED, "extract must apply for the expected source");
		check(extracted.previous().orElseThrow().getStandId().equals(betaId),
				"extract must report the removed Stand");
		check(extracted.current().isEmpty(), "extract must report an empty current Stand");
		check(emptyPower.current.isEmpty(), "extract must clear stored state");
		check(emptyPower.writes == 3, "extract must perform exactly one write");
	}

	private static void assertFailure(
			Result result,
			Status expectedStatus,
			FakePower power,
			int expectedWrites,
			Optional<StandInstance> expectedState) {
		check(result.status() == expectedStatus,
				"expected " + expectedStatus + ", got " + result.status());
		check(!result.applied(), "failed transition must not report applied");
		check(result.previous().isEmpty() && result.current().isEmpty(),
				"failed transition must not expose snapshots");
		check(power.writes == expectedWrites, "failed transition must not write state");
		check(power.current.equals(expectedState), "failed transition must not mutate state");
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakePower implements PowerAccess {
		private final boolean clientSide;
		private Optional<StandInstance> current;
		private int writes;

		private FakePower(boolean clientSide, Optional<StandInstance> current) {
			this.clientSide = clientSide;
			this.current = current;
		}

		@Override
		public boolean isServerSide() {
			return !clientSide;
		}

		@Override
		public Optional<StandInstance> getStandInstance() {
			return current;
		}

		@Override
		public void setStandInstance(Optional<StandInstance> standInstance) {
			current = standInstance;
			writes++;
		}
	}
}

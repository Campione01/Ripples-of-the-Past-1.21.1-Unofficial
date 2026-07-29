package com.github.standobyte.jojo.api.stand;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Operation;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.MutationOperation;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.PowerAccess;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Result;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.Status;
import com.github.standobyte.jojo.api.stand.StandPowerTransitions.TransitionContext;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance.StandPart;
import com.github.standobyte.jojo.powersystem.standpower.TestStandInstances;

import net.minecraft.resources.ResourceLocation;

public final class StandPowerTransitionsSmokeTest {
	private static final StandPowerTransitions.TransitionVeto
			ALLOW_TRANSITION = query -> true;
	private static final StandPowerTransitions.TransitionVeto
			DENY_TRANSITION = query -> false;
	private static final StandPowerTransitions.MutationVeto
			ALLOW_MUTATION = query -> true;
	private static final StandPowerTransitions.MutationVeto
			DENY_MUTATION = query -> false;

	private static final ResourceLocation ALPHA_SKIN =
			id("jojo_ripples", "test_alpha_skin");
	private static final ResourceLocation CHANGED_SKIN =
			id("jojo_ripples", "test_changed_skin");

	private StandPowerTransitionsSmokeTest() {}

	public static void run() {
		verifyRegistrationReplay();
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

		runMutationTransitions(alphaId, betaId);
		runDestructiveTransitions(alphaId);
	}

	private static void verifyRegistrationReplay() {
		StandPowerTransitions.resetVetoesForTests();
		ResourceLocation transitionOwner =
				id("rotp_test", "transition_replay");
		StandPowerTransitions.registerVeto(
				transitionOwner, ALLOW_TRANSITION);
		StandPowerTransitions.registerVeto(
				transitionOwner, ALLOW_TRANSITION);
		check(StandPowerTransitions.registeredVetoOwners()
						.equals(java.util.List.of(transitionOwner)),
				"identical transition veto replay added another owner");
		expectIllegalState(() ->
				StandPowerTransitions.registerVeto(
						transitionOwner, DENY_TRANSITION));

		ResourceLocation mutationOwner =
				id("rotp_test", "mutation_replay");
		StandPowerTransitions.registerMutationVeto(
				mutationOwner, ALLOW_MUTATION);
		StandPowerTransitions.registerMutationVeto(
				mutationOwner, ALLOW_MUTATION);
		check(StandPowerTransitions
						.registeredMutationVetoOwners()
						.equals(java.util.List.of(mutationOwner)),
				"identical mutation veto replay added another owner");
		expectIllegalState(() ->
				StandPowerTransitions.registerMutationVeto(
						mutationOwner, DENY_MUTATION));

		ResourceLocation capturedOwner =
				id("rotp_test", "captured_replay");
		StandPowerTransitions.TransitionVeto captured =
				transitionVeto(true);
		StandPowerTransitions.registerVeto(
				capturedOwner, captured);
		StandPowerTransitions.registerVeto(
				capturedOwner, captured);
		expectIllegalState(() ->
				StandPowerTransitions.registerVeto(
						capturedOwner, transitionVeto(true)));
		StandPowerTransitions.resetVetoesForTests();
	}

	private static StandPowerTransitions.TransitionVeto
			transitionVeto(boolean decision) {
		return query -> decision;
	}

	private static void runMutationTransitions(
			ResourceLocation alphaId,
			ResourceLocation betaId) {
		TransitionContext context = new TransitionContext(
				id("jojo_ripples", "stand_disc"), null);
		StandInstance alpha = TestStandInstances.valid(alphaId);
		StandInstance beta = TestStandInstances.valid(betaId);

		StandPowerTransitions.resetVetoesForTests();
		FakePower offThread =
				new FakePower(false, Optional.empty());
		offThread.serverThread = false;
		assertFailure(
				StandPowerTransitions.insert(
						offThread, beta, context),
				Status.NOT_SERVER_THREAD,
				offThread,
				0,
				Optional.empty());

		FakePower invalidContext =
				new FakePower(false, Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(
						invalidContext, beta, null),
				Status.INVALID_CONTEXT,
				invalidContext,
				0,
				Optional.empty());

		AtomicInteger vetoCalls = new AtomicInteger();
		ResourceLocation vetoOwner =
				id("rotp_test", "d4c_mutation_veto");
		StandPowerTransitions.registerMutationVeto(
				vetoOwner,
				query -> {
					vetoCalls.incrementAndGet();
					check(query.operation()
									== MutationOperation.REPLACE,
							"mutation veto operation changed");
					check(query.context().equals(context),
							"mutation veto context changed");
					StandInstance mutableCurrent =
							query.current().orElseThrow();
					mutableCurrent.removePart(StandPart.ARMS);
					StandInstance mutableReplacement =
							query.replacement();
					mutableReplacement.removePart(StandPart.LEGS);
					return true;
				});
		check(StandPowerTransitions
						.registeredMutationVetoOwners()
						.equals(java.util.List.of(vetoOwner)),
				"mutation veto owner order changed");
		expectIllegalState(() ->
				StandPowerTransitions.registerMutationVeto(
						vetoOwner, query -> false));

		FakePower vetoed =
				new FakePower(false, Optional.of(alpha.copy()));
		assertFailure(
				StandPowerTransitions.replace(
						vetoed,
						alphaId,
						beta,
						context),
				Status.VETOED,
				vetoed,
				0,
				Optional.of(alpha));
		check(vetoCalls.get() == 1,
				"mutation veto callback count changed");
		check(vetoed.current.orElseThrow()
						.hasPart(StandPart.ARMS),
				"mutating the current veto snapshot changed state");
		check(beta.hasPart(StandPart.LEGS),
				"mutating the replacement veto snapshot changed input");

		StandPowerTransitions.resetVetoesForTests();
		StandPowerTransitions.registerMutationVeto(
				id("rotp_test", "mutation_failure"),
				query -> {
					throw new IllegalStateException(
							"mutation preflight failure");
				});
		FakePower failedPreflight =
				new FakePower(false, Optional.empty());
		assertFailure(
				StandPowerTransitions.insert(
						failedPreflight, beta, context),
				Status.PREFLIGHT_FAILED,
				failedPreflight,
				0,
				Optional.empty());

		StandPowerTransitions.resetVetoesForTests();
		FakePower inserted =
				new FakePower(false, Optional.empty());
		Result insertResult = StandPowerTransitions.insert(
				inserted, beta, context);
		check(insertResult.applied(),
				"contextual insert must apply");
		check(inserted.writes == 1
						&& inserted.current.orElseThrow()
								.equals(beta),
				"contextual insert must perform one commit");

		FakePower replaced =
				new FakePower(false, Optional.of(alpha.copy()));
		Result replaceResult = StandPowerTransitions.replace(
				replaced, alphaId, beta, context);
		check(replaceResult.applied(),
				"contextual replace must apply");
		check(replaced.writes == 1
						&& replaced.current.orElseThrow()
								.equals(beta),
				"contextual replace must perform one commit");
		StandPowerTransitions.resetVetoesForTests();
	}

	private static void runDestructiveTransitions(ResourceLocation alphaId) {
		ResourceLocation source =
				id("jojo_ripples", "stand_remover_one_time");
		TransitionContext context = new TransitionContext(source, null);
		StandInstance alpha = TestStandInstances.valid(alphaId);

		StandPowerTransitions.resetVetoesForTests();
		FakePower offThread = new FakePower(false, Optional.of(alpha.copy()));
		offThread.serverThread = false;
		assertFailure(
				StandPowerTransitions.clear(offThread, context),
				Status.NOT_SERVER_THREAD,
				offThread,
				0,
				Optional.of(alpha));

		FakePower invalidContext =
				new FakePower(false, Optional.of(alpha.copy()));
		assertFailure(
				StandPowerTransitions.clear(invalidContext, null),
				Status.INVALID_CONTEXT,
				invalidContext,
				0,
				Optional.of(alpha));
		assertFailure(
				StandPowerTransitions.extract(
						invalidContext, null, context),
				Status.INVALID_SOURCE,
				invalidContext,
				0,
				Optional.of(alpha));

		AtomicInteger vetoCalls = new AtomicInteger();
		ResourceLocation vetoOwner = id("rotp_test", "d4c_veto");
		StandPowerTransitions.registerVeto(vetoOwner, query -> {
			vetoCalls.incrementAndGet();
			check(query.operation() == Operation.CLEAR,
					"veto query operation changed");
			check(query.context().equals(context),
					"veto query context changed");
			StandInstance mutableSnapshot = query.current();
			mutableSnapshot.removePart(StandPart.ARMS);
			return true;
		});
		check(StandPowerTransitions.registeredVetoOwners()
				.equals(java.util.List.of(vetoOwner)),
				"veto owner order changed");
		expectIllegalState(() -> StandPowerTransitions.registerVeto(
				vetoOwner, query -> false));

		FakePower vetoed = new FakePower(false, Optional.of(alpha.copy()));
		assertFailure(
				StandPowerTransitions.clear(vetoed, context),
				Status.VETOED,
				vetoed,
				0,
				Optional.of(alpha));
		check(vetoCalls.get() == 1, "veto callback count changed");
		check(vetoed.current.orElseThrow().hasPart(StandPart.ARMS),
				"mutating a veto snapshot changed stored state");

		StandPowerTransitions.resetVetoesForTests();
		StandPowerTransitions.registerVeto(
				id("rotp_test", "whitesnake_failure"),
				query -> {
					throw new IllegalStateException("preflight failure");
				});
		FakePower failedPreflight =
				new FakePower(false, Optional.of(alpha.copy()));
		assertFailure(
				StandPowerTransitions.fullReset(failedPreflight, context),
				Status.PREFLIGHT_FAILED,
				failedPreflight,
				0,
				Optional.of(alpha));

		StandPowerTransitions.resetVetoesForTests();
		FakePower cleared = new FakePower(false, Optional.of(alpha.copy()));
		Result clearResult = StandPowerTransitions.clear(cleared, context);
		check(clearResult.applied(), "contextual clear must apply");
		check(clearResult.previous().orElseThrow().equals(alpha),
				"contextual clear lost the exact previous Stand");
		check(clearResult.current().isEmpty(),
				"contextual clear must report no current Stand");
		check(cleared.current.isEmpty() && cleared.writes == 1,
				"contextual clear must perform one commit");
		check(!cleared.fullResetApplied,
				"contextual clear must preserve persistent progression");

		FakePower mismatch = new FakePower(false, Optional.of(alpha.copy()));
		assertFailure(
				StandPowerTransitions.extract(
						mismatch,
						id("jojo_ripples", "wrong_stand"),
						context),
				Status.SOURCE_MISMATCH,
				mismatch,
				0,
				Optional.of(alpha));

		FakePower fullReset =
				new FakePower(false, Optional.of(alpha.copy()));
		Result fullResetResult =
				StandPowerTransitions.fullReset(fullReset, context);
		check(fullResetResult.applied(),
				"contextual full reset must apply");
		check(fullReset.current.isEmpty() && fullReset.writes == 1,
				"contextual full reset must perform one commit");
		check(fullReset.fullResetApplied,
				"contextual full reset must request progression erasure");

		StandInstance resultCopy =
				fullResetResult.previous().orElseThrow();
		resultCopy.removePart(StandPart.ARMS);
		check(fullResetResult.previous().orElseThrow()
				.hasPart(StandPart.ARMS),
				"contextual Result snapshots must be defensive");
		StandPowerTransitions.resetVetoesForTests();
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

	private static void expectIllegalState(Runnable action) {
		try {
			action.run();
			throw new AssertionError("expected IllegalStateException");
		}
		catch (IllegalStateException expected) {
			// Expected.
		}
	}

	private static final class FakePower implements PowerAccess {
		private final boolean clientSide;
		private boolean serverThread = true;
		private Optional<StandInstance> current;
		private int writes;
		private boolean fullResetApplied;

		private FakePower(boolean clientSide, Optional<StandInstance> current) {
			this.clientSide = clientSide;
			this.current = current;
		}

		@Override
		public boolean isServerSide() {
			return !clientSide;
		}

		@Override
		public boolean isServerThread() {
			return serverThread;
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

		@Override
		public void applyDestructiveTransition(boolean fullReset) {
			current = Optional.empty();
			fullResetApplied = fullReset;
			writes++;
		}
	}
}

package com.github.standobyte.jojo.subsystems.rollback;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.standobyte.jojo.api.RotpAddonApi;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.Outcome;
import com.github.standobyte.jojo.api.rollback.AtomicRollbackJournal.Status;
import com.github.standobyte.jojo.api.rollback.RollbackAdapterDescriptor;
import com.github.standobyte.jojo.api.rollback.RollbackAdapterRegistry;
import com.github.standobyte.jojo.api.rollback.RollbackCapability;
import com.github.standobyte.jojo.api.rollback.RollbackCapturePolicy;
import com.github.standobyte.jojo.api.rollback.RollbackHandle;
import com.github.standobyte.jojo.api.rollback.RollbackInvalidationReason;
import com.github.standobyte.jojo.api.rollback.RollbackReadiness;
import com.github.standobyte.jojo.api.rollback.RollbackReason;
import com.github.standobyte.jojo.api.rollback.RollbackResult;
import com.github.standobyte.jojo.api.rollback.RollbackScope;
import com.github.standobyte.jojo.api.rollback.RollbackSupport;
import com.github.standobyte.jojo.api.rollback.RollbackSupportMatrix;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class RollbackTransactionFoundationSmokeTest {
	private static final RollbackScope ONE_CHUNK_SCOPE =
			new RollbackScope(new BlockPos(0, 0, 0), new BlockPos(15, 15, 15));

	private RollbackTransactionFoundationSmokeTest() {}

	public static void main(String[] args) {
		run();
		System.out.println(
				"Rollback transaction foundation focused smoke passed.");
	}

	public static void run() {
		supportMatrixRemainsUnavailable();
		policyBoundsAreHard();
		ringIsBoundedAndContiguous();
		authorityChecksRejectInvalidCallers();
		lifecycleAndUsageLimitsInvalidate();
		transactionCountsAreBounded();
		adapterRegistryIsDeterministicAndConstrained();
		atomicJournalCompensatesAttemptedEntries();
		handleAndManagerDoNotRetainWorldOwnersStatically();
	}

	private static void supportMatrixRemainsUnavailable() {
		check(!RotpAddonApi.supportsFeature("rollback_transaction_v1"),
				"rollback feature must not be advertised");
		expectNoField(
				RotpAddonApi.class, "FEATURE_ROLLBACK_TRANSACTION_V1");
		check(!RollbackSupportMatrix.isTransactionAvailable(),
				"transaction support must remain unavailable");
		for (RollbackCapability capability : RollbackCapability.values()) {
			check(RollbackSupportMatrix.support(capability)
					== RollbackSupport.UNSUPPORTED,
					"capability was advertised without an atomic implementation: "
							+ capability);
		}
		expectIllegalArgument(() -> new RollbackReadiness(
				RollbackReadiness.Status.READY,
				RollbackReason.NONE,
				120,
				120,
				Set.of()));
		expectIllegalArgument(() -> new RollbackResult(
				RollbackResult.Status.APPLIED,
				RollbackReason.NONE,
				RollbackReadiness.unsupported(120)));
	}

	private static void policyBoundsAreHard() {
		RollbackCapturePolicy policy = policy(1, 1, 1, 128L, 100L, 200);
		check(policy.scope().chunkCount() == 1L,
				"one-chunk scope count changed");
		check(policy.requiredCapabilities().equals(
				RollbackSupportMatrix.requiredCapabilities()),
				"six-second policy must require the complete matrix");
		expectIllegalArgument(() -> new RollbackCapturePolicy(
				ONE_CHUNK_SCOPE,
				RollbackCapturePolicy.MAX_CHUNKS + 1,
				1,
				1,
				1,
				1L,
				1L,
				120,
				Set.of()));
		expectIllegalArgument(() -> new RollbackCapturePolicy(
				ONE_CHUNK_SCOPE,
				1,
				1,
				1,
				1,
				1L,
				1L,
				RollbackCapturePolicy.HISTORY_INTERVALS - 1,
				Set.of()));
		expectIllegalArgument(() -> new RollbackCapturePolicy(
				new RollbackScope(
						new BlockPos(0, 0, 0), new BlockPos(16, 15, 15)),
				1,
				1,
				1,
				1,
				1L,
				1L,
				120,
				Set.of()));
	}

	private static void ringIsBoundedAndContiguous() {
		UUID epoch = UUID.randomUUID();
		AtomicBoolean serverThread = new AtomicBoolean(true);
		RollbackTransactionLedger ledger = new RollbackTransactionLedger(
				Level.OVERWORLD, epoch, serverThread::get, 0L);
		UUID owner = UUID.randomUUID();
		RollbackHandle handle = ledger.begin(
				owner, policy(2, 2, 2, 128L, 100L, 200));
		for (long tick = 1L; tick <= 150L; tick++) {
			ledger.tick(tick);
		}
		check(ledger.frameBoundaryCount(handle)
				== RollbackCapturePolicy.FRAME_BOUNDARY_CAPACITY,
				"ring must retain exactly 121 boundaries");
		RollbackReadiness readiness = ledger.readiness(handle);
		check(readiness.status() == RollbackReadiness.Status.UNSUPPORTED,
				"foundation readiness must be unsupported");
		check(readiness.availableIntervals()
				== RollbackCapturePolicy.HISTORY_INTERVALS,
				"121 boundaries must expose 120 intervals");
		check(!readiness.ready(), "unsupported readiness cannot be ready");

		RollbackResult wrongDuration = ledger.commit(
				owner, Level.OVERWORLD, handle, 119);
		check(wrongDuration.reason()
				== RollbackReason.TICKS_BACK_OUT_OF_RANGE,
				"commit must enforce the fixed six-second duration");
		RollbackResult result = ledger.commit(
				owner, Level.OVERWORLD, handle, 120);
		check(result.status() == RollbackResult.Status.REJECTED,
				"foundation commit must be rejected");
		check(result.reason() == RollbackReason.FEATURE_UNAVAILABLE,
				"foundation commit must stop at its feature gate");
		check(!result.applied(), "foundation commit cannot report applied");

		RollbackTransactionLedger discontinuous =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle discontinuousHandle = discontinuous.begin(
				UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 200));
		discontinuous.tick(1L);
		discontinuous.tick(3L);
		check(discontinuous.frameBoundaryCount(discontinuousHandle) == 1,
				"a capture gap must reset frame continuity");
	}

	private static void authorityChecksRejectInvalidCallers() {
		UUID epoch = UUID.randomUUID();
		AtomicBoolean serverThread = new AtomicBoolean(true);
		RollbackTransactionLedger ledger = new RollbackTransactionLedger(
				Level.OVERWORLD, epoch, serverThread::get, 0L);
		UUID owner = UUID.randomUUID();
		RollbackHandle handle = ledger.begin(
				owner, policy(1, 1, 1, 128L, 100L, 200));

		check(ledger.commit(
				UUID.randomUUID(), Level.OVERWORLD, handle, 120).reason()
				== RollbackReason.OWNER_MISMATCH,
				"wrong owner must be rejected");
		check(ledger.commit(owner, Level.NETHER, handle, 120).reason()
				== RollbackReason.DIMENSION_MISMATCH,
				"wrong dimension must be rejected");

		RollbackHandle wrongEpoch = RollbackHandle.accepted(
				handle.transactionId(),
				owner,
				Level.OVERWORLD,
				UUID.randomUUID());
		check(ledger.readiness(wrongEpoch).reason()
				== RollbackReason.SERVER_EPOCH_MISMATCH,
				"restart epoch mismatch must be rejected");

		RollbackHandle forgedOwner = RollbackHandle.accepted(
				handle.transactionId(),
				UUID.randomUUID(),
				Level.OVERWORLD,
				epoch);
		check(ledger.readiness(forgedOwner).reason()
				== RollbackReason.OWNER_MISMATCH,
				"handle owner metadata must match manager state");

		serverThread.set(false);
		check(ledger.readiness(handle).reason()
				== RollbackReason.NOT_SERVER_THREAD,
				"off-thread readiness must be rejected");
		RollbackHandle offThreadBegin = ledger.begin(
				owner, policy(1, 1, 1, 128L, 100L, 200));
		check(!offThreadBegin.wasAccepted()
				&& offThreadBegin.initialReason()
						== RollbackReason.NOT_SERVER_THREAD,
				"off-thread begin must return a rejected handle");
	}

	private static void lifecycleAndUsageLimitsInvalidate() {
		RollbackTransactionLedger usageLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle usageHandle = usageLedger.begin(
				UUID.randomUUID(), policy(1, 2, 2, 128L, 100L, 200));
		long firstSequence = usageLedger.recordUsage(
				usageHandle, new RollbackUsage(1, 0, 0, 0L, 0L));
		long secondSequence = usageLedger.recordUsage(
				usageHandle, new RollbackUsage(1, 0, 0, 0L, 0L));
		check(firstSequence == 1L && secondSequence == 2L,
				"journal sequences must be monotonic");
		check(usageLedger.readiness(usageHandle).reason()
				== RollbackReason.POLICY_LIMIT_EXCEEDED,
				"usage overflow must invalidate instead of truncating");

		RollbackTransactionLedger byteLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle byteHandle = byteLedger.begin(
				UUID.randomUUID(), policy(2, 2, 2, 4L, 100L, 200));
		byteLedger.recordUsage(
				byteHandle, new RollbackUsage(0, 0, 0, 5L, 0L));
		check(byteLedger.readiness(byteHandle).reason()
				== RollbackReason.SERIALIZED_BYTES_LIMIT,
				"serialized-byte overflow needs a distinct reason");

		RollbackTransactionLedger expiryLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle expiryHandle = expiryLedger.begin(
				UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 120));
		for (long tick = 1L; tick <= 121L; tick++) {
			expiryLedger.tick(tick);
		}
		check(expiryLedger.readiness(expiryHandle).status()
				== RollbackReadiness.Status.EXPIRED,
				"expired transactions need a distinct readiness state");

		RollbackTransactionLedger unloadedLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle unloadedHandle = unloadedLedger.begin(
				UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 200));
		unloadedLedger.invalidateUnloadedScopes(scope -> false);
		check(unloadedLedger.readiness(unloadedHandle).reason()
				== RollbackReason.CHUNK_UNLOADED,
				"unloaded declared chunks must invalidate");

		RollbackTransactionLedger explicitLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		RollbackHandle explicitHandle = explicitLedger.begin(
				UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 200));
		explicitLedger.invalidate(
				explicitHandle, RollbackInvalidationReason.EXPLICIT);
		check(explicitLedger.readiness(explicitHandle).reason()
				== RollbackReason.INVALIDATED,
				"explicit invalidation must be retained");
	}

	private static void transactionCountsAreBounded() {
		RollbackTransactionLedger activeLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		for (int i = 0;
				i < RollbackTransactionLedger.MAX_ACTIVE_TRANSACTIONS;
				i++) {
			check(activeLedger.begin(
					UUID.randomUUID(),
					policy(1, 1, 1, 128L, 100L, 200)).wasAccepted(),
					"transaction below active limit was rejected");
		}
		RollbackHandle overflow = activeLedger.begin(
				UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 200));
		check(!overflow.wasAccepted()
				&& overflow.initialReason()
						== RollbackReason.ACTIVE_TRANSACTION_LIMIT,
				"active transaction limit must reject the next begin");

		RollbackTransactionLedger retainedLedger =
				new RollbackTransactionLedger(
						Level.OVERWORLD,
						UUID.randomUUID(),
						() -> true,
						0L);
		for (int i = 0; i < 40; i++) {
			RollbackHandle handle = retainedLedger.begin(
					UUID.randomUUID(), policy(1, 1, 1, 128L, 100L, 200));
			retainedLedger.invalidate(
					handle, RollbackInvalidationReason.EXPLICIT);
		}
		check(retainedLedger.transactionCount()
				<= RollbackTransactionLedger.MAX_RETAINED_TRANSACTIONS,
				"terminal handle retention must remain bounded");
	}

	private static void adapterRegistryIsDeterministicAndConstrained() {
		RollbackAdapterRegistry registry = new RollbackAdapterRegistry();
		RollbackAdapterDescriptor later = descriptor(
				"later", 10, RollbackCapability.ADDON_STATE, true);
		RollbackAdapterDescriptor earlier = descriptor(
				"earlier", -10,
				RollbackCapability.ALLOWLISTED_WORLD_STATE, true);
		registry.register(later);
		registry.register(earlier);
		check(registry.applyOrder().get(0).id().equals(earlier.id()),
				"adapter apply ordering must be deterministic");
		expectIllegalArgument(() -> registry.register(later));
		expectIllegalArgument(() -> registry.register(
				descriptor("core_claim", 0, RollbackCapability.PLAYER, true)));
		expectIllegalArgument(() -> registry.register(
				descriptor("no_inverse", 0,
						RollbackCapability.ADDON_STATE, false)));
		registry.freeze();
		check(registry.isFrozen(), "adapter registry did not freeze");
		expectIllegalState(() -> registry.register(
				descriptor("late", 0, RollbackCapability.ADDON_STATE, true)));
	}

	private static void atomicJournalCompensatesAttemptedEntries() {
		List<String> trace = new ArrayList<>();
		Outcome applied = AtomicRollbackJournal.apply(List.of(
				AtomicRollbackJournal.entry(
						"first",
						() -> trace.add("apply:first"),
						() -> trace.add("inverse:first")),
				AtomicRollbackJournal.entry(
						"second",
						() -> trace.add("apply:second"),
						() -> trace.add("inverse:second"))));
		check(applied.status() == Status.APPLIED
				&& applied.attemptedEntries() == 2
				&& applied.failure() == null,
				"a successful journal did not report APPLIED");
		check(trace.equals(List.of("apply:first", "apply:second")),
				"a successful journal unexpectedly ran an inverse");

		trace.clear();
		RuntimeException applyFailure =
				new IllegalStateException("apply failed");
		Outcome restored = AtomicRollbackJournal.apply(List.of(
				AtomicRollbackJournal.entry(
						"first",
						() -> trace.add("apply:first"),
						() -> trace.add("inverse:first")),
				AtomicRollbackJournal.entry(
						"second",
						() -> {
							trace.add("apply:second");
							throw applyFailure;
						},
						() -> trace.add("inverse:second"))));
		check(restored.status() == Status.ROLLED_BACK
				&& restored.fullyRestored()
				&& restored.failure() == applyFailure,
				"a compensated journal did not retain its apply failure");
		check(trace.equals(List.of(
				"apply:first",
				"apply:second",
				"inverse:second",
				"inverse:first")),
				"attempted entries were not compensated in reverse order");

		trace.clear();
		RuntimeException inverseFailure =
				new IllegalStateException("inverse failed");
		Outcome incomplete = AtomicRollbackJournal.apply(List.of(
				AtomicRollbackJournal.entry(
						"first",
						() -> trace.add("apply:first"),
						() -> trace.add("inverse:first")),
				AtomicRollbackJournal.entry(
						"second",
						() -> {
							trace.add("apply:second");
							throw applyFailure;
						},
						() -> {
							trace.add("inverse:second");
							throw inverseFailure;
						})));
		check(incomplete.status() == Status.INVERSE_FAILED
				&& incomplete.inverseFailures().size() == 1
				&& incomplete.inverseFailures().get(0)
						.entryId().equals("second")
				&& incomplete.inverseFailures().get(0)
						.failure() == inverseFailure,
				"an inverse failure was not reported explicitly");
		check(trace.equals(List.of(
				"apply:first",
				"apply:second",
				"inverse:second",
				"inverse:first")),
				"one inverse failure stopped later compensation");

		trace.clear();
		RuntimeException sharedFailure =
				new IllegalStateException("shared failure");
		Outcome selfSuppressed = AtomicRollbackJournal.apply(List.of(
				AtomicRollbackJournal.entry(
						"first",
						() -> trace.add("apply:first"),
						() -> trace.add("inverse:first")),
				AtomicRollbackJournal.entry(
						"second",
						() -> {
							trace.add("apply:second");
							throw sharedFailure;
						},
						() -> {
							trace.add("inverse:second");
							throw sharedFailure;
						})));
		check(selfSuppressed.status() == Status.INVERSE_FAILED
				&& selfSuppressed.failure() == sharedFailure
				&& selfSuppressed.inverseFailures().size() == 1
				&& selfSuppressed.inverseFailures().get(0)
						.failure() == sharedFailure,
				"a self-suppressed inverse failure was not reported");
		check(trace.equals(List.of(
				"apply:first",
				"apply:second",
				"inverse:second",
				"inverse:first")),
				"a self-suppressed inverse stopped later compensation");

		AtomicBoolean mutated = new AtomicBoolean();
		expectIllegalArgument(() -> AtomicRollbackJournal.apply(List.of(
				AtomicRollbackJournal.entry(
						"duplicate",
						() -> mutated.set(true),
						() -> {}),
				AtomicRollbackJournal.entry(
						"duplicate",
						() -> mutated.set(true),
						() -> {}))));
		check(!mutated.get(),
				"journal structure was not validated before mutation");

		List<AtomicRollbackJournal.Entry> oversized =
				new ArrayList<>();
		for (int index = 0;
				index <= AtomicRollbackJournal.MAX_ENTRIES;
				index++) {
			oversized.add(AtomicRollbackJournal.entry(
					"entry_" + index,
					() -> mutated.set(true),
					() -> {}));
		}
		expectIllegalArgument(() -> AtomicRollbackJournal.apply(oversized));
		check(!mutated.get(),
				"journal size was not validated before mutation");
	}

	private static void handleAndManagerDoNotRetainWorldOwnersStatically() {
		for (Field field : RollbackHandle.class.getDeclaredFields()) {
			check(!Entity.class.isAssignableFrom(field.getType())
					&& !Player.class.isAssignableFrom(field.getType()),
					"opaque handles cannot retain entity objects");
		}
		for (Class<?> type : Set.of(
				RollbackTransactionManager.class,
				RollbackTransactionLedger.class)) {
			for (Field field : type.getDeclaredFields()) {
				check(!(Modifier.isStatic(field.getModifiers())
						&& Map.class.isAssignableFrom(field.getType())),
						"manager state cannot use a static map");
			}
		}
	}

	private static RollbackCapturePolicy policy(
			int maxEntities,
			int maxBlockMutations,
			int maxContainerSlots,
			long maxSerializedBytes,
			long maxCaptureNanos,
			int expiryTicks) {
		return new RollbackCapturePolicy(
				ONE_CHUNK_SCOPE,
				1,
				maxEntities,
				maxBlockMutations,
				maxContainerSlots,
				maxSerializedBytes,
				maxCaptureNanos,
				expiryTicks,
				RollbackSupportMatrix.requiredCapabilities());
	}

	private static RollbackAdapterDescriptor descriptor(
			String path,
			int applyOrder,
			RollbackCapability capability,
			boolean inverseCapable) {
		return new RollbackAdapterDescriptor(
				ResourceLocation.fromNamespaceAndPath("rollback_test", path),
				1,
				0,
				applyOrder,
				128,
				Set.of(capability),
				inverseCapable);
	}

	private static void expectNoField(Class<?> type, String name) {
		try {
			type.getDeclaredField(name);
			throw new AssertionError("unexpected field: " + name);
		}
		catch (NoSuchFieldException expected) {
			// Expected.
		}
	}

	private static void expectIllegalArgument(Runnable action) {
		try {
			action.run();
			throw new AssertionError("expected IllegalArgumentException");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}

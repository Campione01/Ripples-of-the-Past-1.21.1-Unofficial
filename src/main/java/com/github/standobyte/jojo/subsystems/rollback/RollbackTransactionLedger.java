package com.github.standobyte.jojo.subsystems.rollback;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import com.github.standobyte.jojo.api.rollback.RollbackCapturePolicy;
import com.github.standobyte.jojo.api.rollback.RollbackHandle;
import com.github.standobyte.jojo.api.rollback.RollbackInvalidationReason;
import com.github.standobyte.jojo.api.rollback.RollbackReadiness;
import com.github.standobyte.jojo.api.rollback.RollbackReason;
import com.github.standobyte.jojo.api.rollback.RollbackResult;
import com.github.standobyte.jojo.api.rollback.RollbackScope;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Pure bounded lifecycle ledger. It stores frame boundary metadata only and
 * deliberately has no capture or world-apply operation.
 */
final class RollbackTransactionLedger {
	static final int MAX_ACTIVE_TRANSACTIONS = 8;
	static final int MAX_RETAINED_TRANSACTIONS = 32;

	private final ResourceKey<Level> dimension;
	private final UUID serverEpoch;
	private final BooleanSupplier serverThread;
	private final Map<UUID, Transaction> transactions = new LinkedHashMap<>();
	private long currentTick;

	RollbackTransactionLedger(
			ResourceKey<Level> dimension,
			UUID serverEpoch,
			BooleanSupplier serverThread,
			long currentTick) {
		this.dimension = dimension;
		this.serverEpoch = serverEpoch;
		this.serverThread = serverThread;
		this.currentTick = currentTick;
	}

	RollbackHandle begin(UUID ownerId, RollbackCapturePolicy policy) {
		if (!serverThread.getAsBoolean()) {
			return RollbackHandle.rejected(
					ownerId, dimension, RollbackReason.NOT_SERVER_THREAD);
		}
		pruneTerminalTransactions();
		if (activeTransactionCount() >= MAX_ACTIVE_TRANSACTIONS) {
			return RollbackHandle.rejected(
					ownerId, dimension, RollbackReason.ACTIVE_TRANSACTION_LIMIT);
		}

		UUID transactionId;
		do {
			transactionId = UUID.randomUUID();
		}
		while (transactions.containsKey(transactionId));

		RollbackHandle handle = RollbackHandle.accepted(
				transactionId, ownerId, dimension, serverEpoch);
		Transaction transaction =
				new Transaction(handle, policy, currentTick);
		transactions.put(transactionId, transaction);
		return handle;
	}

	RollbackReadiness readiness(RollbackHandle handle) {
		Validation validation = validate(handle);
		if (validation.failure() != null) {
			return validation.failure();
		}
		Transaction transaction = validation.transaction();
		if (!transaction.active) {
			return RollbackReadiness.invalid(transaction.outcomeReason);
		}
		return RollbackReadiness.unsupported(transaction.availableIntervals());
	}

	RollbackResult commit(
			UUID requesterId,
			ResourceKey<Level> requesterDimension,
			RollbackHandle handle,
			int ticksBack) {
		Validation validation = validate(handle);
		if (validation.failure() != null) {
			return RollbackResult.rejected(
					validation.failure().reason(), validation.failure());
		}
		Transaction transaction = validation.transaction();
		if (!requesterId.equals(transaction.handle.ownerId())) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(RollbackReason.OWNER_MISMATCH);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		if (!requesterDimension.equals(dimension)) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(RollbackReason.DIMENSION_MISMATCH);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		if (!transaction.active) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(transaction.outcomeReason);
			return RollbackResult.rejected(failure.reason(), failure);
		}
		if (ticksBack != RollbackCapturePolicy.HISTORY_INTERVALS) {
			RollbackReadiness failure =
					RollbackReadiness.invalid(
							RollbackReason.TICKS_BACK_OUT_OF_RANGE);
			return RollbackResult.rejected(failure.reason(), failure);
		}

		// The feature gate precedes history/preflight. No apply path exists.
		RollbackReadiness unsupported =
				RollbackReadiness.unsupported(transaction.availableIntervals());
		return RollbackResult.rejected(
				RollbackReason.FEATURE_UNAVAILABLE, unsupported);
	}

	void tick(long gameTick) {
		if (!serverThread.getAsBoolean() || gameTick == currentTick) {
			return;
		}
		currentTick = gameTick;
		for (Transaction transaction : transactions.values()) {
			if (!transaction.active) {
				continue;
			}
			if (gameTick > transaction.expiryTick) {
				invalidate(transaction, RollbackInvalidationReason.EXPIRED);
				continue;
			}
			transaction.openFrame(gameTick);
		}
		pruneTerminalTransactions();
	}

	void invalidate(
			RollbackHandle handle, RollbackInvalidationReason invalidationReason) {
		Validation validation = validate(handle);
		if (validation.failure() == null && validation.transaction().active) {
			invalidate(validation.transaction(), invalidationReason);
		}
	}

	void invalidateOwner(
			UUID ownerId, RollbackInvalidationReason invalidationReason) {
		if (!serverThread.getAsBoolean()) {
			return;
		}
		for (Transaction transaction : transactions.values()) {
			if (transaction.active
					&& transaction.handle.ownerId().equals(ownerId)) {
				invalidate(transaction, invalidationReason);
			}
		}
	}

	void invalidateAll(RollbackInvalidationReason invalidationReason) {
		if (!serverThread.getAsBoolean()) {
			return;
		}
		for (Transaction transaction : transactions.values()) {
			if (transaction.active) {
				invalidate(transaction, invalidationReason);
			}
		}
	}

	void invalidateUnloadedScopes(Predicate<RollbackScope> scopeLoaded) {
		if (!serverThread.getAsBoolean()) {
			return;
		}
		for (Transaction transaction : transactions.values()) {
			if (transaction.active
					&& !scopeLoaded.test(transaction.policy.scope())) {
				invalidate(
						transaction, RollbackInvalidationReason.CHUNK_UNLOADED);
			}
		}
	}

	long recordUsage(RollbackHandle handle, RollbackUsage delta) {
		Validation validation = validate(handle);
		if (validation.failure() != null || !validation.transaction().active) {
			return -1L;
		}
		Transaction transaction = validation.transaction();
		long sequence = ++transaction.journalSequence;
		transaction.usage = transaction.usage.plus(delta);
		RollbackReason limitReason = limitReason(transaction);
		if (limitReason != RollbackReason.NONE) {
			transaction.active = false;
			transaction.invalidationReason =
					RollbackInvalidationReason.LIMIT_EXCEEDED;
			transaction.outcomeReason = limitReason;
		}
		return sequence;
	}

	int frameBoundaryCount(RollbackHandle handle) {
		Transaction transaction = transactions.get(handle.transactionId());
		return transaction == null ? 0 : transaction.frameBoundaries.size();
	}

	RollbackScope scope(RollbackHandle handle) {
		Validation validation = validate(handle);
		return validation.failure() == null
				? validation.transaction().policy.scope()
				: null;
	}

	int activeTransactionCount() {
		int count = 0;
		for (Transaction transaction : transactions.values()) {
			if (transaction.active) {
				count++;
			}
		}
		return count;
	}

	int transactionCount() {
		return transactions.size();
	}

	private Validation validate(RollbackHandle handle) {
		if (!serverThread.getAsBoolean()) {
			return Validation.failure(RollbackReason.NOT_SERVER_THREAD);
		}
		if (handle.initialReason() != RollbackReason.NONE) {
			return Validation.failure(handle.initialReason());
		}
		if (!serverEpoch.equals(handle.serverEpoch())) {
			return Validation.failure(RollbackReason.SERVER_EPOCH_MISMATCH);
		}
		if (!dimension.equals(handle.dimension())) {
			return Validation.failure(RollbackReason.DIMENSION_MISMATCH);
		}
		Transaction transaction = transactions.get(handle.transactionId());
		if (transaction == null) {
			return Validation.failure(RollbackReason.UNKNOWN_HANDLE);
		}
		if (!transaction.handle.ownerId().equals(handle.ownerId())) {
			return Validation.failure(RollbackReason.OWNER_MISMATCH);
		}
		return new Validation(transaction, null);
	}

	private RollbackReason limitReason(Transaction transaction) {
		RollbackCapturePolicy policy = transaction.policy;
		RollbackUsage usage = transaction.usage;
		if (usage.serializedBytes() > policy.maxSerializedBytes()) {
			return RollbackReason.SERIALIZED_BYTES_LIMIT;
		}
		if (usage.captureNanos() > policy.maxCaptureNanosPerTick()) {
			return RollbackReason.CAPTURE_TIME_LIMIT;
		}
		if (usage.entities() > policy.maxEntities()
				|| usage.blockMutations() > policy.maxBlockMutations()
				|| usage.containerSlots() > policy.maxContainerSlots()) {
			return RollbackReason.POLICY_LIMIT_EXCEEDED;
		}
		return RollbackReason.NONE;
	}

	private void invalidate(
			Transaction transaction,
			RollbackInvalidationReason invalidationReason) {
		transaction.active = false;
		transaction.invalidationReason = invalidationReason;
		transaction.outcomeReason = invalidationReason.outcomeReason();
	}

	private void pruneTerminalTransactions() {
		if (transactions.size() < MAX_RETAINED_TRANSACTIONS) {
			return;
		}
		Iterator<Transaction> iterator = transactions.values().iterator();
		while (transactions.size() >= MAX_RETAINED_TRANSACTIONS
				&& iterator.hasNext()) {
			if (!iterator.next().active) {
				iterator.remove();
			}
		}
	}

	private record Validation(
			Transaction transaction, RollbackReadiness failure) {
		static Validation failure(RollbackReason reason) {
			return new Validation(null, RollbackReadiness.invalid(reason));
		}
	}

	private static final class Transaction {
		private final RollbackHandle handle;
		private final RollbackCapturePolicy policy;
		private final long expiryTick;
		private final ArrayDeque<Long> frameBoundaries = new ArrayDeque<>();
		private RollbackUsage usage = RollbackUsage.ZERO;
		private long journalSequence;
		private boolean active = true;
		private RollbackInvalidationReason invalidationReason;
		private RollbackReason outcomeReason = RollbackReason.NONE;

		private Transaction(
				RollbackHandle handle,
				RollbackCapturePolicy policy,
				long startTick) {
			this.handle = handle;
			this.policy = policy;
			this.expiryTick = saturatedAdd(startTick, policy.expiryTicks());
			frameBoundaries.addLast(startTick);
		}

		private void openFrame(long gameTick) {
			Long previous = frameBoundaries.peekLast();
			if (previous != null && gameTick != previous + 1L) {
				frameBoundaries.clear();
			}
			frameBoundaries.addLast(gameTick);
			while (frameBoundaries.size()
					> RollbackCapturePolicy.FRAME_BOUNDARY_CAPACITY) {
				frameBoundaries.removeFirst();
			}
			usage = usage.nextTick();
		}

		private int availableIntervals() {
			return Math.max(0, frameBoundaries.size() - 1);
		}

		private static long saturatedAdd(long left, long right) {
			try {
				return Math.addExact(left, right);
			}
			catch (ArithmeticException overflow) {
				return Long.MAX_VALUE;
			}
		}
	}
}

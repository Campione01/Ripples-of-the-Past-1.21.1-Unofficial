package com.github.standobyte.jojo.api.rollback;

import java.util.List;
import java.util.Objects;

import com.github.standobyte.jojo.subsystems.rollback.AtomicRollbackJournalExecutor;

/**
 * Applies a bounded list of mutations and compensates attempted entries in
 * reverse order when one mutation fails.
 *
 * <p>This is an execution primitive, not world-history capture. Callers must
 * finish their domain preflight and capture every inverse before calling
 * {@link #apply(List)}. Operational failures must be reported as
 * {@link RuntimeException}; VM errors are not recoverable transaction
 * outcomes.</p>
 */
public final class AtomicRollbackJournal {
	public static final int MAX_ENTRIES = 512;
	public static final int MAX_ENTRY_ID_LENGTH = 128;

	private AtomicRollbackJournal() {}

	public static Entry entry(
			String id, Runnable apply, Runnable inverse) {
		return new Entry(id, apply, inverse);
	}

	public static Outcome apply(List<Entry> entries) {
		return AtomicRollbackJournalExecutor.apply(entries);
	}

	public record Entry(String id, Runnable apply, Runnable inverse) {
		public Entry {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(apply, "apply");
			Objects.requireNonNull(inverse, "inverse");
			if (id.isBlank() || id.length() > MAX_ENTRY_ID_LENGTH) {
				throw new IllegalArgumentException(
						"rollback journal entry id must contain 1-"
								+ MAX_ENTRY_ID_LENGTH + " characters");
			}
		}
	}

	public enum Status {
		APPLIED,
		ROLLED_BACK,
		INVERSE_FAILED
	}

	public record InverseFailure(
			String entryId, RuntimeException failure) {
		public InverseFailure {
			Objects.requireNonNull(entryId, "entryId");
			Objects.requireNonNull(failure, "failure");
		}
	}

	public record Outcome(
			Status status,
			int attemptedEntries,
			RuntimeException failure,
			List<InverseFailure> inverseFailures) {
		public Outcome {
			Objects.requireNonNull(status, "status");
			inverseFailures = List.copyOf(
					Objects.requireNonNull(
							inverseFailures, "inverseFailures"));
			if (attemptedEntries < 0 || attemptedEntries > MAX_ENTRIES) {
				throw new IllegalArgumentException(
						"attempted rollback journal entry count is invalid");
			}
			if (status == Status.APPLIED
					&& (failure != null || !inverseFailures.isEmpty())) {
				throw new IllegalArgumentException(
						"an applied journal cannot contain failures");
			}
			if (status == Status.ROLLED_BACK
					&& (failure == null || !inverseFailures.isEmpty())) {
				throw new IllegalArgumentException(
						"a restored journal requires only an apply failure");
			}
			if (status == Status.INVERSE_FAILED
					&& (failure == null || inverseFailures.isEmpty())) {
				throw new IllegalArgumentException(
						"an inverse failure requires apply and inverse errors");
			}
		}

		public boolean applied() {
			return status == Status.APPLIED;
		}

		public boolean fullyRestored() {
			return status == Status.ROLLED_BACK;
		}
	}
}

package com.github.standobyte.jojo.api.rollback;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable readiness snapshot. The core never exposes captured snapshots.
 */
public record RollbackReadiness(
		Status status,
		RollbackReason reason,
		int availableIntervals,
		int requiredIntervals,
		Set<RollbackCapability> missingCapabilities) {

	public enum Status {
		CAPTURING,
		READY,
		UNSUPPORTED,
		INVALID,
		EXPIRED
	}

	public RollbackReadiness {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(missingCapabilities, "missingCapabilities");
		if (availableIntervals < 0 || requiredIntervals < 0) {
			throw new IllegalArgumentException("interval counts cannot be negative");
		}
		missingCapabilities = missingCapabilities.isEmpty()
				? Collections.emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(missingCapabilities));
		if (status == Status.READY
				&& (!RollbackSupportMatrix.isTransactionAvailable()
						|| reason != RollbackReason.NONE
						|| availableIntervals < requiredIntervals
						|| !missingCapabilities.isEmpty())) {
			throw new IllegalArgumentException(
					"READY requires complete atomic support and history");
		}
	}

	public static RollbackReadiness unsupported(int availableIntervals) {
		return new RollbackReadiness(
				Status.UNSUPPORTED,
				RollbackReason.FEATURE_UNAVAILABLE,
				availableIntervals,
				RollbackCapturePolicy.HISTORY_INTERVALS,
				RollbackSupportMatrix.unsupportedRequiredCapabilities());
	}

	public static RollbackReadiness invalid(RollbackReason reason) {
		Status status = reason == RollbackReason.EXPIRED
				? Status.EXPIRED
				: Status.INVALID;
		return new RollbackReadiness(
				status,
				reason,
				0,
				RollbackCapturePolicy.HISTORY_INTERVALS,
				RollbackSupportMatrix.unsupportedRequiredCapabilities());
	}

	public boolean ready() {
		return status == Status.READY;
	}
}

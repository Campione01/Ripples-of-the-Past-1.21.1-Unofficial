package com.github.standobyte.jojo.api.rollback;

import java.util.Objects;

/**
 * Immutable commit outcome. APPLIED is reserved for a future implementation
 * with complete preflight and inverse-journal recovery.
 */
public record RollbackResult(
		Status status,
		RollbackReason reason,
		RollbackReadiness readiness) {

	public enum Status {
		REJECTED,
		ABORTED,
		APPLIED
	}

	public RollbackResult {
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(readiness, "readiness");
		if (status == Status.APPLIED
				&& (!RollbackSupportMatrix.isTransactionAvailable()
						|| reason != RollbackReason.NONE
						|| !readiness.ready())) {
			throw new IllegalArgumentException(
					"APPLIED requires complete atomic support and readiness");
		}
	}

	public static RollbackResult rejected(
			RollbackReason reason, RollbackReadiness readiness) {
		return new RollbackResult(Status.REJECTED, reason, readiness);
	}

	public boolean applied() {
		return status == Status.APPLIED;
	}
}

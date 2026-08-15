package com.github.standobyte.jojo.network.c2s;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounds stack-trace logging without affecting ability packet handling. */
final class AbilityInputFailureLogLimiter {
	static final int DEFAULT_MAX_CLASSIFICATIONS = 128;
	static final long DEFAULT_CLASSIFICATION_WINDOW_NANOS =
			Duration.ofSeconds(30).toNanos();
	static final long DEFAULT_GLOBAL_WINDOW_NANOS =
			Duration.ofSeconds(5).toNanos();
	static final int DEFAULT_MAX_GLOBAL_LOGS = 8;
	static final long DEFAULT_IDLE_EXPIRY_NANOS =
			Duration.ofMinutes(5).toNanos();

	private static final AbilityInputFailureLogLimiter INSTANCE =
			new AbilityInputFailureLogLimiter(
					DEFAULT_MAX_CLASSIFICATIONS,
					DEFAULT_CLASSIFICATION_WINDOW_NANOS,
					DEFAULT_GLOBAL_WINDOW_NANOS,
					DEFAULT_MAX_GLOBAL_LOGS,
					DEFAULT_IDLE_EXPIRY_NANOS);

	private final int maxClassifications;
	private final long classificationWindowNanos;
	private final long globalWindowNanos;
	private final int maxGlobalLogs;
	private final long idleExpiryNanos;
	private final Map<FailureKey, FailureBucket> failures =
			new LinkedHashMap<>();
	private long globalWindowStartNanos;
	private int globalLogs;
	private boolean globalWindowStarted;

	static Decision acquire(
			UUID playerUuid,
			String abilityId,
			Class<? extends Throwable> errorType) {
		return INSTANCE.acquire(
				playerUuid,
				abilityId,
				errorType,
				System.nanoTime());
	}

	AbilityInputFailureLogLimiter(
			int maxClassifications,
			long classificationWindowNanos,
			long globalWindowNanos,
			int maxGlobalLogs,
			long idleExpiryNanos) {
		if (maxClassifications <= 0 || classificationWindowNanos <= 0L
				|| globalWindowNanos <= 0L || maxGlobalLogs <= 0
				|| idleExpiryNanos < classificationWindowNanos) {
			throw new IllegalArgumentException("Invalid ability failure log limits");
		}
		this.maxClassifications = maxClassifications;
		this.classificationWindowNanos = classificationWindowNanos;
		this.globalWindowNanos = globalWindowNanos;
		this.maxGlobalLogs = maxGlobalLogs;
		this.idleExpiryNanos = idleExpiryNanos;
	}

	synchronized Decision acquire(
			UUID playerUuid,
			String abilityId,
			Class<? extends Throwable> errorType,
			long nowNanos) {
		resetGlobalWindowIfNeeded(nowNanos);
		FailureKey key = new FailureKey(
				playerUuid,
				abilityId != null ? abilityId : "unresolved",
				errorType != null ? errorType.getName() : "unknown");
		FailureBucket bucket = failures.get(key);
		if (bucket == null) {
			removeIdleClassifications(nowNanos);
			if (failures.size() >= maxClassifications) {
				return Decision.suppressedAtCapacity();
			}
			bucket = new FailureBucket();
			failures.put(key, bucket);
		}

		bucket.lastSeenNanos = nowNanos;
		boolean classificationReady = !bucket.logged
				|| elapsed(nowNanos, bucket.lastLoggedNanos)
						>= classificationWindowNanos;
		if (!classificationReady || globalLogs >= maxGlobalLogs) {
			bucket.suppressedCount++;
			return Decision.suppressed();
		}

		long suppressedCount = bucket.suppressedCount;
		bucket.suppressedCount = 0L;
		bucket.lastLoggedNanos = nowNanos;
		bucket.logged = true;
		globalLogs++;
		return Decision.log(suppressedCount);
	}

	synchronized int classificationCount() {
		return failures.size();
	}

	private void resetGlobalWindowIfNeeded(long nowNanos) {
		if (!globalWindowStarted
				|| elapsed(nowNanos, globalWindowStartNanos)
						>= globalWindowNanos) {
			globalWindowStartNanos = nowNanos;
			globalLogs = 0;
			globalWindowStarted = true;
		}
	}

	private void removeIdleClassifications(long nowNanos) {
		Iterator<FailureBucket> iterator = failures.values().iterator();
		while (iterator.hasNext()) {
			FailureBucket bucket = iterator.next();
			if (elapsed(nowNanos, bucket.lastSeenNanos) >= idleExpiryNanos) {
				iterator.remove();
			}
		}
	}

	private static long elapsed(long nowNanos, long thenNanos) {
		return nowNanos >= thenNanos ? nowNanos - thenNanos : Long.MAX_VALUE;
	}

	private record FailureKey(
			UUID playerUuid,
			String abilityId,
			String errorType) {}

	private static final class FailureBucket {
		private long lastLoggedNanos;
		private long lastSeenNanos;
		private long suppressedCount;
		private boolean logged;
	}

	record Decision(
			boolean logStackTrace,
			long suppressedCount,
			boolean classificationCapacityReached) {
		private static Decision log(long suppressedCount) {
			return new Decision(true, suppressedCount, false);
		}

		private static Decision suppressed() {
			return new Decision(false, 0L, false);
		}

		private static Decision suppressedAtCapacity() {
			return new Decision(false, 0L, true);
		}
	}
}

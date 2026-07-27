package com.github.standobyte.jojo.api.rollback;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Hard-bounded capture request. Passing validation does not imply that the
 * support matrix can currently execute the request.
 */
public record RollbackCapturePolicy(
		RollbackScope scope,
		int maxChunks,
		int maxEntities,
		int maxBlockMutations,
		int maxContainerSlots,
		long maxSerializedBytes,
		long maxCaptureNanosPerTick,
		int expiryTicks,
		Set<RollbackCapability> requiredCapabilities) {

	public static final int FRAME_BOUNDARY_CAPACITY = 121;
	public static final int HISTORY_INTERVALS = 120;
	public static final int MAX_CHUNKS = 25;
	public static final int MAX_ENTITIES = 256;
	public static final int MAX_BLOCK_MUTATIONS = 4_096;
	public static final int MAX_CONTAINER_SLOTS = 2_048;
	public static final long MAX_SERIALIZED_BYTES = 8L * 1_024L * 1_024L;
	public static final long MAX_CAPTURE_NANOS_PER_TICK = 2_000_000L;
	public static final int MAX_EXPIRY_TICKS = 200;

	public RollbackCapturePolicy {
		Objects.requireNonNull(scope, "scope");
		Objects.requireNonNull(requiredCapabilities, "requiredCapabilities");
		requiredCapabilities = requiredCapabilities.isEmpty()
				? Collections.emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(requiredCapabilities));
		requireBounded("maxChunks", maxChunks, MAX_CHUNKS);
		requireBounded("maxEntities", maxEntities, MAX_ENTITIES);
		requireBounded("maxBlockMutations", maxBlockMutations,
				MAX_BLOCK_MUTATIONS);
		requireBounded("maxContainerSlots", maxContainerSlots,
				MAX_CONTAINER_SLOTS);
		requireBounded("maxSerializedBytes", maxSerializedBytes,
				MAX_SERIALIZED_BYTES);
		requireBounded("maxCaptureNanosPerTick", maxCaptureNanosPerTick,
				MAX_CAPTURE_NANOS_PER_TICK);
		if (expiryTicks < HISTORY_INTERVALS || expiryTicks > MAX_EXPIRY_TICKS) {
			throw new IllegalArgumentException(
					"expiryTicks must be in [" + HISTORY_INTERVALS + ", "
							+ MAX_EXPIRY_TICKS + "]");
		}
		if (scope.chunkCount() > maxChunks) {
			throw new IllegalArgumentException(
					"declared scope exceeds maxChunks");
		}
	}

	public static RollbackCapturePolicy sixSeconds(RollbackScope scope) {
		long chunks = scope.chunkCount();
		if (chunks > MAX_CHUNKS) {
			throw new IllegalArgumentException(
					"declared scope exceeds the core chunk limit");
		}
		return new RollbackCapturePolicy(
				scope,
				(int) chunks,
				MAX_ENTITIES,
				MAX_BLOCK_MUTATIONS,
				MAX_CONTAINER_SLOTS,
				MAX_SERIALIZED_BYTES,
				MAX_CAPTURE_NANOS_PER_TICK,
				MAX_EXPIRY_TICKS,
				RollbackSupportMatrix.requiredCapabilities());
	}

	private static void requireBounded(String name, long value, long hardLimit) {
		if (value <= 0L || value > hardLimit) {
			throw new IllegalArgumentException(
					name + " must be in [1, " + hardLimit + "]");
		}
	}
}

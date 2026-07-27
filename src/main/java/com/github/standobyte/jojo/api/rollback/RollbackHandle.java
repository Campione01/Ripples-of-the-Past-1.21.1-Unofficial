package com.github.standobyte.jojo.api.rollback;

import java.util.Objects;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable transaction identity. It contains no captured world state.
 */
public final class RollbackHandle {
	private static final UUID NO_SERVER_EPOCH = new UUID(0L, 0L);

	private final UUID transactionId;
	private final UUID ownerId;
	private final ResourceKey<Level> dimension;
	private final UUID serverEpoch;
	private final RollbackReason initialReason;

	private RollbackHandle(
			UUID transactionId,
			UUID ownerId,
			ResourceKey<Level> dimension,
			UUID serverEpoch,
			RollbackReason initialReason) {
		this.transactionId = Objects.requireNonNull(transactionId, "transactionId");
		this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
		this.dimension = Objects.requireNonNull(dimension, "dimension");
		this.serverEpoch = Objects.requireNonNull(serverEpoch, "serverEpoch");
		this.initialReason = Objects.requireNonNull(initialReason, "initialReason");
	}

	@ApiStatus.Internal
	public static RollbackHandle accepted(
			UUID transactionId,
			UUID ownerId,
			ResourceKey<Level> dimension,
			UUID serverEpoch) {
		return new RollbackHandle(
				transactionId, ownerId, dimension, serverEpoch, RollbackReason.NONE);
	}

	@ApiStatus.Internal
	public static RollbackHandle rejected(
			UUID ownerId,
			ResourceKey<Level> dimension,
			RollbackReason reason) {
		if (reason == RollbackReason.NONE) {
			throw new IllegalArgumentException("a rejected handle requires a reason");
		}
		return new RollbackHandle(
				UUID.randomUUID(), ownerId, dimension, NO_SERVER_EPOCH, reason);
	}

	public UUID transactionId() {
		return transactionId;
	}

	public UUID ownerId() {
		return ownerId;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	@ApiStatus.Internal
	public UUID serverEpoch() {
		return serverEpoch;
	}

	@ApiStatus.Internal
	public RollbackReason initialReason() {
		return initialReason;
	}

	public boolean wasAccepted() {
		return initialReason == RollbackReason.NONE;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof RollbackHandle other
				&& transactionId.equals(other.transactionId)
				&& ownerId.equals(other.ownerId)
				&& dimension.equals(other.dimension)
				&& serverEpoch.equals(other.serverEpoch)
				&& initialReason == other.initialReason;
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				transactionId, ownerId, dimension, serverEpoch, initialReason);
	}

	@Override
	public String toString() {
		return "RollbackHandle[" + transactionId + "]";
	}
}

package com.github.standobyte.jojo.api.rollback;

/**
 * Lifecycle reason supplied when an active transaction is invalidated.
 */
public enum RollbackInvalidationReason {
	EXPLICIT,
	OWNER_LOGOUT,
	OWNER_CHANGED_DIMENSION,
	LEVEL_UNLOAD,
	SERVER_STOPPING,
	EXPIRED,
	LIMIT_EXCEEDED,
	CHUNK_UNLOADED,
	UNSUPPORTED_MUTATION;

	public RollbackReason outcomeReason() {
		return switch (this) {
		case EXPIRED -> RollbackReason.EXPIRED;
		case LIMIT_EXCEEDED -> RollbackReason.POLICY_LIMIT_EXCEEDED;
		case CHUNK_UNLOADED -> RollbackReason.CHUNK_UNLOADED;
		case UNSUPPORTED_MUTATION -> RollbackReason.UNSUPPORTED_CAPABILITY;
		default -> RollbackReason.INVALIDATED;
		};
	}
}

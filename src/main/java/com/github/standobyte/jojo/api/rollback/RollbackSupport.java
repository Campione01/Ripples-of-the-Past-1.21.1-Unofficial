package com.github.standobyte.jojo.api.rollback;

/**
 * Atomicity level currently provided by the core for a rollback surface.
 */
public enum RollbackSupport {
	UNSUPPORTED,
	ADAPTER_ONLY,
	CORE_ATOMIC
}

package com.github.standobyte.jojo.api.rollback;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Current core support. This matrix is intentionally independent from ABI
 * type availability and from adapter descriptor registration.
 */
public final class RollbackSupportMatrix {
	private static final Set<RollbackCapability> REQUIRED_CAPABILITIES =
			Collections.unmodifiableSet(EnumSet.allOf(RollbackCapability.class));
	private static final Map<RollbackCapability, RollbackSupport> SUPPORT;

	static {
		EnumMap<RollbackCapability, RollbackSupport> support =
				new EnumMap<>(RollbackCapability.class);
		for (RollbackCapability capability : RollbackCapability.values()) {
			support.put(capability, RollbackSupport.UNSUPPORTED);
		}
		SUPPORT = Collections.unmodifiableMap(support);
	}

	private RollbackSupportMatrix() {}

	public static RollbackSupport support(RollbackCapability capability) {
		return SUPPORT.get(capability);
	}

	public static Map<RollbackCapability, RollbackSupport> snapshot() {
		return SUPPORT;
	}

	public static Set<RollbackCapability> requiredCapabilities() {
		return REQUIRED_CAPABILITIES;
	}

	public static Set<RollbackCapability> unsupportedRequiredCapabilities() {
		EnumSet<RollbackCapability> unsupported =
				EnumSet.noneOf(RollbackCapability.class);
		for (RollbackCapability capability : REQUIRED_CAPABILITIES) {
			if (support(capability) != RollbackSupport.CORE_ATOMIC) {
				unsupported.add(capability);
			}
		}
		return Collections.unmodifiableSet(unsupported);
	}

	public static boolean isTransactionAvailable() {
		for (RollbackCapability capability : REQUIRED_CAPABILITIES) {
			if (support(capability) != RollbackSupport.CORE_ATOMIC) {
				return false;
			}
		}
		return true;
	}
}

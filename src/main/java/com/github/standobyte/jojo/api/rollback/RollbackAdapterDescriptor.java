package com.github.standobyte.jojo.api.rollback;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * Registration metadata for a future bounded rollback state adapter.
 *
 * <p>Registration reserves identity and ordering only. It cannot make a
 * capability operational while the support matrix is unavailable.</p>
 */
public record RollbackAdapterDescriptor(
		ResourceLocation id,
		int codecVersion,
		int captureOrder,
		int applyOrder,
		int maxSerializedBytes,
		Set<RollbackCapability> capabilities,
		boolean inverseCapable) {

	public RollbackAdapterDescriptor {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(capabilities, "capabilities");
		if (codecVersion <= 0) {
			throw new IllegalArgumentException("codecVersion must be positive");
		}
		if (captureOrder < -10_000 || captureOrder > 10_000
				|| applyOrder < -10_000 || applyOrder > 10_000) {
			throw new IllegalArgumentException("adapter order is out of range");
		}
		if (maxSerializedBytes <= 0
				|| maxSerializedBytes > RollbackCapturePolicy.MAX_SERIALIZED_BYTES) {
			throw new IllegalArgumentException(
					"adapter byte limit is out of range");
		}
		capabilities = capabilities.isEmpty()
				? Collections.emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(capabilities));
	}
}

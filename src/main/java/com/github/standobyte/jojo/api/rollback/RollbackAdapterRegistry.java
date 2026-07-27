package com.github.standobyte.jojo.api.rollback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic registry of bounded adapter descriptors.
 */
public final class RollbackAdapterRegistry {
	public static final int MAX_ADAPTERS = 64;
	public static final long MAX_TOTAL_DECLARED_BYTES =
			RollbackCapturePolicy.MAX_SERIALIZED_BYTES;

	private static final Set<RollbackCapability> ADAPTER_CAPABILITIES = Set.of(
			RollbackCapability.ALLOWLISTED_WORLD_STATE,
			RollbackCapability.ADDON_STATE);

	private final Map<ResourceLocation, RollbackAdapterDescriptor> descriptors =
			new LinkedHashMap<>();
	private long totalDeclaredBytes;
	private boolean frozen;

	public synchronized void register(RollbackAdapterDescriptor descriptor) {
		Objects.requireNonNull(descriptor, "descriptor");
		if (frozen) {
			throw new IllegalStateException("rollback adapter registry is frozen");
		}
		if (!descriptor.inverseCapable()) {
			throw new IllegalArgumentException(
					"rollback adapters must guarantee inverse application");
		}
		if (descriptor.capabilities().isEmpty()
				|| !ADAPTER_CAPABILITIES.containsAll(descriptor.capabilities())) {
			throw new IllegalArgumentException(
					"adapter descriptors may only claim addon or allowlisted world state");
		}
		if (descriptors.size() >= MAX_ADAPTERS) {
			throw new IllegalStateException("rollback adapter limit reached");
		}
		if (totalDeclaredBytes + descriptor.maxSerializedBytes()
				> MAX_TOTAL_DECLARED_BYTES) {
			throw new IllegalArgumentException(
					"rollback adapter byte declarations exceed the registry limit");
		}
		if (descriptors.putIfAbsent(descriptor.id(), descriptor) != null) {
			throw new IllegalArgumentException(
					"duplicate rollback adapter ID: " + descriptor.id());
		}
		totalDeclaredBytes += descriptor.maxSerializedBytes();
	}

	public synchronized Optional<RollbackAdapterDescriptor> find(
			ResourceLocation id) {
		return Optional.ofNullable(descriptors.get(id));
	}

	public synchronized List<RollbackAdapterDescriptor> captureOrder() {
		return orderedBy(RollbackAdapterDescriptor::captureOrder);
	}

	public synchronized List<RollbackAdapterDescriptor> applyOrder() {
		return orderedBy(RollbackAdapterDescriptor::applyOrder);
	}

	private List<RollbackAdapterDescriptor> orderedBy(
			java.util.function.ToIntFunction<RollbackAdapterDescriptor> order) {
		List<RollbackAdapterDescriptor> ordered =
				new ArrayList<>(descriptors.values());
		ordered.sort(Comparator
				.comparingInt(order)
				.thenComparing(descriptor -> descriptor.id().toString()));
		return Collections.unmodifiableList(ordered);
	}

	public synchronized void freeze() {
		frozen = true;
	}

	public synchronized boolean isFrozen() {
		return frozen;
	}
}

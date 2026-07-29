package com.github.standobyte.jojo.api.stand;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.resources.ResourceLocation;

/**
 * Additive, owner-scoped exclusions from the Stand Arrow selection pool.
 * <p>
 * The underlying Stand tag and random weight remain owned by each Stand. An
 * addon can therefore restore a combination-specific exclusion without
 * mutating another addon's registry object or tag membership.
 */
public final class StandArrowPoolOverrides {
	private static final Object LOCK = new Object();
	private static final Map<ResourceLocation, Set<ResourceLocation>>
			EXCLUSIONS_BY_OWNER = new LinkedHashMap<>();
	private static final Map<ResourceLocation, Set<ResourceLocation>>
			OWNERS_BY_STAND = new LinkedHashMap<>();

	private StandArrowPoolOverrides() {}

	/**
	 * Excludes a Stand while the registering owner is loaded.
	 * Repeating the same owner/Stand pair is an idempotent no-op.
	 */
	public static void exclude(
			ResourceLocation ownerId,
			ResourceLocation standId) {
		Objects.requireNonNull(ownerId, "ownerId");
		Objects.requireNonNull(standId, "standId");
		synchronized (LOCK) {
			if (EXCLUSIONS_BY_OWNER
					.computeIfAbsent(
							ownerId, __ -> new LinkedHashSet<>())
					.add(standId)) {
				OWNERS_BY_STAND
						.computeIfAbsent(
								standId, __ -> new LinkedHashSet<>())
						.add(ownerId);
			}
		}
	}

	public static boolean isExcluded(ResourceLocation standId) {
		if (standId == null) {
			return false;
		}
		synchronized (LOCK) {
			return OWNERS_BY_STAND.containsKey(standId);
		}
	}

	/**
	 * Returns an immutable snapshot for diagnostics and compatibility tests.
	 */
	public static Set<ResourceLocation> ownersExcluding(
			ResourceLocation standId) {
		if (standId == null) {
			return Set.of();
		}
		synchronized (LOCK) {
			return Set.copyOf(
					OWNERS_BY_STAND.getOrDefault(
							standId, Set.of()));
		}
	}

	@ApiStatus.Internal
	public static void resetForTests() {
		synchronized (LOCK) {
			EXCLUSIONS_BY_OWNER.clear();
			OWNERS_BY_STAND.clear();
		}
	}
}

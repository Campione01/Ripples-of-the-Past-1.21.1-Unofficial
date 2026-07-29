package com.github.standobyte.jojo.api.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed, deny-dominant physical hand-use blockers.
 *
 * <p>Providers are queried in registration order on both logical sides.
 * Provider failures leave the core hand-free result unchanged.</p>
 */
public final class LivingHandUseBlockers {
	private static final Map<ResourceLocation, LivingHandUseBlocker>
			BLOCKERS = new LinkedHashMap<>();

	private LivingHandUseBlockers() {}

	public static synchronized void register(
			ResourceLocation owner,
			LivingHandUseBlocker blocker) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(blocker, "blocker");
		if (BLOCKERS.putIfAbsent(owner, blocker) != null) {
			throw new IllegalStateException(
					"Duplicate living hand-use blocker: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean isBlocked(
			LivingEntity entity,
			InteractionHand hand) {
		Objects.requireNonNull(entity, "entity");
		Objects.requireNonNull(hand, "hand");
		List<Map.Entry<ResourceLocation, LivingHandUseBlocker>>
				snapshot;
		synchronized (LivingHandUseBlockers.class) {
			snapshot = new ArrayList<>(BLOCKERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, LivingHandUseBlocker>
				entry : snapshot) {
			try {
				if (entry.getValue().isBlocked(entity, hand)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Living hand-use blocker {} failed.",
						entry.getKey(),
						error);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(BLOCKERS.keySet());
	}

	static synchronized void resetForTests() {
		BLOCKERS.clear();
	}
}

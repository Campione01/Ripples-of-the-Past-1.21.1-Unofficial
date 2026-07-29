package com.github.standobyte.jojo.api.timestop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Owner-keyed predicates that may additionally allow an entity to move while
 * time is stopped. Predicates should be side-effect free.
 */
public final class TimeStopEntityMovementAuthorizers {
	private static final Map<ResourceLocation, Predicate<Entity>>
			AUTHORIZERS = new LinkedHashMap<>();

	private TimeStopEntityMovementAuthorizers() {}

	public static synchronized void register(
			ResourceLocation owner,
			Predicate<Entity> authorizer) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(authorizer, "authorizer");
		if (AUTHORIZERS.putIfAbsent(owner, authorizer) != null) {
			throw new IllegalStateException(
					"Duplicate time-stop entity movement authorizer: "
							+ owner);
		}
	}

	@ApiStatus.Internal
	public static boolean canMoveInStoppedTime(Entity entity) {
		return entity != null && evaluate(entity);
	}

	static boolean evaluate(Entity entity) {
		List<Map.Entry<ResourceLocation, Predicate<Entity>>> snapshot;
		synchronized (TimeStopEntityMovementAuthorizers.class) {
			snapshot = new ArrayList<>(AUTHORIZERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, Predicate<Entity>>
				entry : snapshot) {
			try {
				if (entry.getValue().test(entity)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Time-stop entity movement authorizer {} failed.",
						entry.getKey(),
						error);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(AUTHORIZERS.keySet());
	}

	static synchronized void resetForTests() {
		AUTHORIZERS.clear();
	}
}

package com.github.standobyte.jojo.api.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Owner-keyed predicates for addon items that count as leaving a hand free.
 * Predicates should be side-effect free.
 */
public final class ItemHandFreePredicates {
	private static final Map<ResourceLocation, ItemHandFreePredicate>
			PREDICATES = new LinkedHashMap<>();

	private ItemHandFreePredicates() {}

	public static synchronized void register(
			ResourceLocation owner,
			ItemHandFreePredicate predicate) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(predicate, "predicate");
		if (PREDICATES.putIfAbsent(owner, predicate) != null) {
			throw new IllegalStateException(
					"Duplicate item hand-free predicate: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean matches(ItemStack stack) {
		Objects.requireNonNull(stack, "stack");
		List<Map.Entry<ResourceLocation, ItemHandFreePredicate>>
				snapshot;
		synchronized (ItemHandFreePredicates.class) {
			snapshot = new ArrayList<>(PREDICATES.entrySet());
		}
		for (Map.Entry<ResourceLocation, ItemHandFreePredicate>
				entry : snapshot) {
			try {
				if (entry.getValue().isHandFree(stack)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Item hand-free predicate {} failed.",
						entry.getKey(),
						error);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PREDICATES.keySet());
	}

	static synchronized void resetForTests() {
		PREDICATES.clear();
	}
}

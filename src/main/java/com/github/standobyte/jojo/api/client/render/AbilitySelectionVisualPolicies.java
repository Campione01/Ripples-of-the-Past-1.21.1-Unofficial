package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.Power;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ARGB32;

/**
 * Client-only, owner-keyed tint policies for ROTP ability selection layers.
 */
public final class AbilitySelectionVisualPolicies {
	private static final Map<ResourceLocation, AbilitySelectionVisualPolicy>
			POLICIES = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private AbilitySelectionVisualPolicies() {}

	public static synchronized void register(
			ResourceLocation owner,
			AbilitySelectionVisualPolicy policy) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(policy, "policy");
		if (POLICIES.putIfAbsent(owner, policy) != null) {
			throw new IllegalStateException(
					"Duplicate ability selection visual policy: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(POLICIES.size());
		POLICIES.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	public static int selectionTint(
			Ability ability,
			Power<?> power,
			ConditionCheck conditionCheck,
			AbilitySelectionSurface surface,
			int defaultTint) {
		return selectionTintOverride(
				ability,
				power,
				conditionCheck,
				surface,
				defaultTint).orElse(defaultTint);
	}

	/**
	 * Returns the alpha-multiplied tint only when a registered policy claims
	 * the queried selection surface.
	 */
	public static OptionalInt selectionTintOverride(
			Ability ability,
			Power<?> power,
			ConditionCheck conditionCheck,
			AbilitySelectionSurface surface,
			int defaultTint) {
		List<Registration> registrations = snapshot;
		if (registrations.isEmpty()) {
			return OptionalInt.empty();
		}
		AbilitySelectionVisualQuery query =
				new AbilitySelectionVisualQuery(
						Objects.requireNonNull(ability, "ability"),
						power,
						Objects.requireNonNull(
								conditionCheck, "conditionCheck"),
						Objects.requireNonNull(surface, "surface"));
		for (Registration registration : registrations) {
			try {
				OptionalInt tint = Objects.requireNonNull(
						registration.policy().selectionTint(query),
						"selection tint");
				if (tint.isPresent()) {
					return OptionalInt.of(ARGB32.multiply(
							defaultTint, tint.getAsInt()));
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Ability selection visual policy {} failed.",
						registration.owner(),
						e);
			}
		}
		return OptionalInt.empty();
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(POLICIES.keySet());
	}

	static synchronized void resetForTests() {
		POLICIES.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			AbilitySelectionVisualPolicy policy) {}
}

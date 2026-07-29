package com.github.standobyte.jojo.api.playerpower;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPowerType;

import net.minecraft.resources.ResourceLocation;

/**
 * Exclusive owner-keyed delegation bindings for temporary PlayerPower types.
 *
 * <p>A binding does not create or store another power. It only authorizes the
 * core temporary-transition state to use its already-retained type for native
 * moveset, action-data, ticking, and query behavior.</p>
 */
public final class PlayerPowerDelegations {
	private static final Map<ResourceLocation, Binding> BY_OWNER =
			new LinkedHashMap<>();
	private static final Map<ResourceLocation, Binding> BY_TEMPORARY_TYPE =
			new LinkedHashMap<>();

	private PlayerPowerDelegations() {}

	public static synchronized void register(
			ResourceLocation owner,
			ResourceLocation temporaryType,
			PlayerPowerDelegationProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(temporaryType, "temporaryType");
		Objects.requireNonNull(provider, "provider");
		if (BY_OWNER.containsKey(owner)) {
			throw new IllegalStateException(
					"Duplicate PlayerPower delegation owner: " + owner);
		}
		Binding existing = BY_TEMPORARY_TYPE.get(temporaryType);
		if (existing != null) {
			throw new IllegalStateException(
					"PlayerPower temporary type " + temporaryType
							+ " is already delegated by "
							+ existing.owner());
		}
		Binding binding = new Binding(owner, temporaryType, provider);
		BY_OWNER.put(owner, binding);
		BY_TEMPORARY_TYPE.put(temporaryType, binding);
	}

	public static Optional<PlayerPowerType<?>> delegatedType(
			PlayerPower power) {
		Objects.requireNonNull(power, "power");
		PlayerPowerType<?> current = power.getPowerType();
		PlayerPowerType<?> retained = power.getRetainedTemporaryType();
		return delegates(power, current, retained)
				? Optional.of(retained)
				: Optional.empty();
	}

	public static boolean delegates(
			PlayerPower power,
			PlayerPowerType<?> temporaryType,
			PlayerPowerType<?> retainedType) {
		if (power == null
				|| temporaryType == null
				|| retainedType == null
				|| temporaryType == retainedType) {
			return false;
		}
		Binding binding;
		synchronized (PlayerPowerDelegations.class) {
			binding = BY_TEMPORARY_TYPE.get(temporaryType.getId());
		}
		if (binding == null) {
			return false;
		}
		try {
			return binding.provider().delegates(power, retainedType);
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error(
					"PlayerPower delegation {} failed for {} ({} -> {}).",
					binding.owner(),
					power.getUser().getScoreboardName(),
					retainedType.getId(),
					temporaryType.getId(),
					error);
			return false;
		}
	}

	public static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(BY_OWNER.keySet());
	}

	static synchronized void clearForTests() {
		BY_OWNER.clear();
		BY_TEMPORARY_TYPE.clear();
	}

	private record Binding(
			ResourceLocation owner,
			ResourceLocation temporaryType,
			PlayerPowerDelegationProvider provider) {}
}

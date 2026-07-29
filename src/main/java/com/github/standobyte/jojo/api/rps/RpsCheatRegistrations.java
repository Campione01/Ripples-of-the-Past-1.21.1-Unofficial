package com.github.standobyte.jojo.api.rps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;

import net.minecraft.resources.ResourceLocation;

/**
 * Declarative stand registrations for core-owned RPS cheat behavior.
 *
 * <p>This API intentionally exposes no executor callback and no game state.
 * The server re-resolves the player's active Stand before executing a closed
 * cheat kind.</p>
 */
public final class RpsCheatRegistrations {
	private static final Map<ResourceLocation, Registration> REGISTRATIONS =
			new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private RpsCheatRegistrations() {}

	public static synchronized void registerStand(
			ResourceLocation owner,
			Supplier<? extends StandType> standType,
			RpsCheatSpec spec) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(standType, "standType");
		Objects.requireNonNull(spec, "spec");
		Registration registration =
				new Registration(owner, standType, spec);
		if (REGISTRATIONS.putIfAbsent(owner, registration) != null) {
			throw new IllegalStateException(
					"Duplicate RPS cheat registration owner: " + owner);
		}
		snapshot = List.copyOf(
				new ArrayList<>(REGISTRATIONS.values()));
	}

	public static Optional<RpsCheatRegistration> find(
			StandPower power) {
		if (power == null || !power.hasPower()) {
			return Optional.empty();
		}
		return find(power.getPowerType());
	}

	private static Optional<RpsCheatRegistration> find(
			StandType actualType) {
		if (actualType == null) {
			return Optional.empty();
		}
		RpsCheatRegistration match = null;
		for (Registration registration : snapshot) {
			StandType registeredType;
			try {
				registeredType = registration.standType().get();
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"RPS cheat Stand supplier {} failed.",
						registration.owner(),
						error);
				continue;
			}
			if (registeredType != actualType) {
				continue;
			}
			if (match != null) {
				JojoMod.getLogger().error(
						"RPS cheat Stand {} is registered by both {} and {}; "
								+ "the duplicate is rejected.",
						actualType,
						match.owner(),
						registration.owner());
				return Optional.empty();
			}
			match = new RpsCheatRegistration(
					registration.owner(), registration.spec());
		}
		return Optional.ofNullable(match);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(REGISTRATIONS.keySet());
	}

	static synchronized void resetForTests() {
		REGISTRATIONS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			Supplier<? extends StandType> standType,
			RpsCheatSpec spec) {}
}

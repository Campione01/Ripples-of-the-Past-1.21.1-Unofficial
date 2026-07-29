package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed extension point for addon-defined Stand-virus targets.
 */
public final class StandVirusMobGivers {
	private static final Map<ResourceLocation, StandVirusMobGiver> GIVERS =
			new LinkedHashMap<>();
	private static volatile List<Match> snapshot = List.of();

	private StandVirusMobGivers() {}

	public static synchronized void register(
			ResourceLocation owner,
			StandVirusMobGiver giver) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(giver, "giver");
		if (GIVERS.putIfAbsent(owner, giver) != null) {
			throw new IllegalStateException(
					"Duplicate Stand-virus mob giver: " + owner);
		}
		List<Match> updated = new ArrayList<>(GIVERS.size());
		GIVERS.forEach((id, registered) ->
				updated.add(new Match(id, registered)));
		snapshot = List.copyOf(updated);
	}

	public static Optional<Match> find(LivingEntity target) {
		Objects.requireNonNull(target, "target");
		for (Match match : snapshot) {
			try {
				if (match.giver().matches(target)) {
					return Optional.of(match);
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Stand-virus mob giver {} match failed.",
						match.owner(),
						error);
			}
		}
		return Optional.empty();
	}

	public static synchronized Optional<StandVirusMobGiver> get(
			ResourceLocation owner) {
		return Optional.ofNullable(
				GIVERS.get(Objects.requireNonNull(owner, "owner")));
	}

	public static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(GIVERS.keySet());
	}

	@ApiStatus.Internal
	public static synchronized void resetForTests() {
		GIVERS.clear();
		snapshot = List.of();
	}

	public record Match(
			ResourceLocation owner,
			StandVirusMobGiver giver) {}
}

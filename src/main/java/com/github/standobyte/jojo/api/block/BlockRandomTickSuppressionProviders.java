package com.github.standobyte.jojo.api.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Owner-keyed server predicates that can veto a block random tick.
 *
 * <p>Providers run in registration order until one returns {@code true}.
 * They must be side-effect-free and must not consume the tick random source.</p>
 */
public final class BlockRandomTickSuppressionProviders {
	private static final Map<ResourceLocation,
			BlockRandomTickSuppressionProvider> PROVIDERS =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private BlockRandomTickSuppressionProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			BlockRandomTickSuppressionProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate block random-tick suppression provider: "
							+ owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static boolean shouldSuppress(
			ServerLevel level,
			BlockPos position,
			BlockState state) {
		return shouldSuppress(new BlockRandomTickSuppressionQuery(
				level, position, state));
	}

	static boolean shouldSuppress(
			BlockRandomTickSuppressionQuery query) {
		for (Registration registration : snapshot) {
			try {
				if (registration.provider().shouldSuppress(query)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Block random-tick suppression provider {} failed.",
						registration.owner(),
						error);
			}
		}
		return false;
	}

	private static void publishSnapshot() {
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((owner, provider) ->
				registrations.add(new Registration(owner, provider)));
		snapshot = List.copyOf(registrations);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
		snapshot = List.of();
	}

	private record Registration(
			ResourceLocation owner,
			BlockRandomTickSuppressionProvider provider) {}
}

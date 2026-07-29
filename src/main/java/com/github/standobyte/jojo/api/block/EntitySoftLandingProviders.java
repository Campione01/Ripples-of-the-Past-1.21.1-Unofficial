package com.github.standobyte.jojo.api.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed all-entity soft-landing providers. Providers are evaluated in
 * registration order and the first handled decision wins.
 */
public final class EntitySoftLandingProviders {
	private static final Map<ResourceLocation, EntitySoftLandingProvider>
			PROVIDERS = new LinkedHashMap<>();

	private EntitySoftLandingProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			EntitySoftLandingProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate entity soft-landing provider: " + owner);
		}
	}

	@ApiStatus.Internal
	public static EntitySoftLandingDecision resolve(
			EntitySoftLandingQuery query) {
		Objects.requireNonNull(query, "query");
		return resolveProviders(provider -> provider.resolve(query));
	}

	static EntitySoftLandingDecision resolveProviders(
			Function<EntitySoftLandingProvider,
					EntitySoftLandingDecision> resolver) {
		Objects.requireNonNull(resolver, "resolver");
		List<Map.Entry<ResourceLocation, EntitySoftLandingProvider>>
				snapshot;
		synchronized (EntitySoftLandingProviders.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, EntitySoftLandingProvider>
				entry : snapshot) {
			try {
				EntitySoftLandingDecision decision =
						Objects.requireNonNull(
								resolver.apply(entry.getValue()),
								"Entity soft-landing decision");
				if (decision.isHandled()) {
					return decision;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Entity soft-landing provider {} failed.",
						entry.getKey(),
						error);
			}
		}
		return EntitySoftLandingDecision.pass();
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
	}
}

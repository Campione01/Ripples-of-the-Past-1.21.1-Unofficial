package com.github.standobyte.jojo.api.soul;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed extensions for soul Resolve eligibility.
 *
 * <p>Providers are evaluated in registration order. Any denial is final;
 * otherwise at least one allow may override the core default. Provider
 * failures are isolated and leave the current decision unchanged.</p>
 */
public final class SoulResolveEligibilityProviders {
	private static final Map<ResourceLocation, SoulResolveEligibilityProvider>
			PROVIDERS = new LinkedHashMap<>();

	private SoulResolveEligibilityProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			SoulResolveEligibilityProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate soul Resolve eligibility provider: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean isEligible(SoulResolveQuery query) {
		Objects.requireNonNull(query, "query");
		boolean eligible = query.defaultEligibility();
		List<Map.Entry<ResourceLocation, SoulResolveEligibilityProvider>>
				snapshot;
		synchronized (SoulResolveEligibilityProviders.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, SoulResolveEligibilityProvider>
				entry : snapshot) {
			try {
				SoulResolveDecision decision = Objects.requireNonNull(
						entry.getValue().decide(query),
						"provider decision");
				if (decision == SoulResolveDecision.DENY) {
					return false;
				}
				if (decision == SoulResolveDecision.ALLOW) {
					eligible = true;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Soul Resolve eligibility provider {} failed.",
						entry.getKey(),
						e);
			}
		}
		return eligible;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
	}
}

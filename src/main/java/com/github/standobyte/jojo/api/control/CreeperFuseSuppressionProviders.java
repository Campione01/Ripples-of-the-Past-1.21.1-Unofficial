package com.github.standobyte.jojo.api.control;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Owner-keyed Creeper fuse suppression predicates.
 *
 * <p>The core owns the private-field and cancellation hook. Addons only
 * provide a narrow, side-effect-free predicate based on synchronized state.</p>
 */
public final class CreeperFuseSuppressionProviders {
	private static final Map<ResourceLocation, CreeperFuseSuppressionProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private CreeperFuseSuppressionProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			CreeperFuseSuppressionProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate Creeper fuse suppression provider: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	@ApiStatus.Internal
	public static boolean shouldSuppress(Creeper creeper) {
		for (Registration registration : snapshot) {
			try {
				if (registration.provider().shouldSuppress(creeper)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Creeper fuse suppression provider {} failed.",
						registration.owner(),
						error);
			}
		}
		return false;
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
			CreeperFuseSuppressionProvider provider) {}
}

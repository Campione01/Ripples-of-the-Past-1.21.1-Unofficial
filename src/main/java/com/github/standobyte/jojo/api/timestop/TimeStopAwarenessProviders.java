package com.github.standobyte.jojo.api.timestop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Owner-keyed extension point for addon-defined time-stop awareness.
 */
public final class TimeStopAwarenessProviders {
	private static final Map<ResourceLocation, TimeStopAwarenessProvider>
			PROVIDERS = new LinkedHashMap<>();

	private TimeStopAwarenessProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			TimeStopAwarenessProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate time-stop awareness provider: " + owner);
		}
	}

	@ApiStatus.Internal
	public static TimeStopAwareness resolve(ServerPlayer player) {
		Objects.requireNonNull(player, "player");
		List<Map.Entry<ResourceLocation, TimeStopAwarenessProvider>>
				snapshot;
		synchronized (TimeStopAwarenessProviders.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		TimeStopAwareness awareness = TimeStopAwareness.NONE;
		for (Map.Entry<ResourceLocation, TimeStopAwarenessProvider>
				entry : snapshot) {
			try {
				awareness = awareness.merge(
						entry.getValue().getAwareness(player));
				if (awareness.equals(TimeStopAwareness.FULL)) {
					return awareness;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Time-stop awareness provider {} failed.",
						entry.getKey(),
						e);
			}
		}
		return awareness;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
	}
}

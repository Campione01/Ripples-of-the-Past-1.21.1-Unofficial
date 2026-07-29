package com.github.standobyte.jojo.api.stand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.entity.EntityStandType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public final class StandLeapUnlockProviders {
	private static final Map<ResourceLocation, StandLeapUnlockProvider>
			PROVIDERS = new LinkedHashMap<>();

	private StandLeapUnlockProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			StandLeapUnlockProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate Stand leap unlock provider: " + owner);
		}
	}

	@ApiStatus.Internal
	public static boolean unlocks(
			LivingEntity user,
			StandPower power,
			EntityStandType standType) {
		return evaluate(new StandLeapUnlockQuery(
				Objects.requireNonNull(user, "user"),
				Objects.requireNonNull(power, "power"),
				Objects.requireNonNull(standType, "standType")));
	}

	static boolean evaluate(StandLeapUnlockQuery query) {
		List<Map.Entry<ResourceLocation, StandLeapUnlockProvider>>
				snapshot;
		synchronized (StandLeapUnlockProviders.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		for (Map.Entry<ResourceLocation, StandLeapUnlockProvider>
				entry : snapshot) {
			try {
				if (entry.getValue().unlocks(query)) {
					return true;
				}
			}
			catch (RuntimeException e) {
				JojoMod.getLogger().error(
						"Stand leap unlock provider {} failed.",
						entry.getKey(),
						e);
			}
		}
		return false;
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
	}
}

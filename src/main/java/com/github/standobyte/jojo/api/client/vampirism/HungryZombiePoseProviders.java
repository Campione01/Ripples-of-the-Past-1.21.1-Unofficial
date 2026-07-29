package com.github.standobyte.jojo.api.client.vampirism;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only owner-keyed contributors to the Hungry Zombie waiting pose.
 */
@OnlyIn(Dist.CLIENT)
public final class HungryZombiePoseProviders {
	private static final Map<ResourceLocation,
			Predicate<? super HungryZombieEntity>> PROVIDERS =
					new LinkedHashMap<>();

	private HungryZombiePoseProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			Predicate<? super HungryZombieEntity> provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate Hungry Zombie pose provider: "
							+ owner);
		}
	}

	@ApiStatus.Internal
	public static boolean isWaiting(HungryZombieEntity entity) {
		Objects.requireNonNull(entity, "entity");
		List<Map.Entry<ResourceLocation,
				Predicate<? super HungryZombieEntity>>> snapshot;
		synchronized (HungryZombiePoseProviders.class) {
			snapshot = new ArrayList<>(PROVIDERS.entrySet());
		}
		for (Map.Entry<ResourceLocation,
				Predicate<? super HungryZombieEntity>> entry
				: snapshot) {
			try {
				if (entry.getValue().test(entity)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Hungry Zombie pose provider {} failed",
						entry.getKey(),
						error);
			}
		}
		return false;
	}

	static synchronized Set<ResourceLocation> registeredOwners() {
		return Set.copyOf(PROVIDERS.keySet());
	}

	static synchronized void resetForTests() {
		PROVIDERS.clear();
	}
}

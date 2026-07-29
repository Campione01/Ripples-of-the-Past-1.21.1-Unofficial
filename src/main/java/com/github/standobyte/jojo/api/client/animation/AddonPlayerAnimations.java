package com.github.standobyte.jojo.api.client.animation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Resolves addon-owned player animations without replacing or mutating the
 * shared player renderer.
 *
 * <p>Providers are evaluated only when the core has no active player action or
 * persistent core pose. Higher priorities run first, with the provider id used
 * as a deterministic tie-breaker.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class AddonPlayerAnimations {
	private static final Map<ResourceLocation, Entry> PROVIDERS =
			new HashMap<>();
	private static List<Entry> orderedProviders = List.of();

	public static void register(
			ResourceLocation providerId,
			PlayerAnimationProvider provider) {
		register(providerId, 0, provider);
	}

	public static synchronized void register(
			ResourceLocation providerId,
			int priority,
			PlayerAnimationProvider provider) {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(provider, "provider");
		Entry entry = new Entry(providerId, priority, provider);
		if (PROVIDERS.putIfAbsent(providerId, entry) != null) {
			throw new IllegalArgumentException(
					"Player animation provider is already registered: "
							+ providerId);
		}
		ArrayList<Entry> sorted = new ArrayList<>(PROVIDERS.values());
		sorted.sort(Comparator
				.comparingInt(Entry::priority)
				.reversed()
				.thenComparing(entryToSort ->
						entryToSort.id().toString()));
		orderedProviders = List.copyOf(sorted);
	}

	@Nullable
	public static PlayerAnimationState resolve(
			Player player,
			float partialTick) {
		Objects.requireNonNull(player, "player");
		for (Entry entry : orderedProviders) {
			try {
				PlayerAnimationState state =
						entry.provider().resolve(player, partialTick);
				if (state != null) {
					return state;
				}
			}
			catch (RuntimeException exception) {
				JojoMod.getLogger().error(
						"Addon player animation provider {} failed",
						entry.id(),
						exception);
			}
		}
		return null;
	}

	public record PlayerAnimationState(
			ResourceLocation animationSet,
			String animation,
			float timeInTicks) {
		public PlayerAnimationState {
			Objects.requireNonNull(animationSet, "animationSet");
			Objects.requireNonNull(animation, "animation");
			if (animation.isBlank()) {
				throw new IllegalArgumentException(
						"animation must not be blank");
			}
			if (!Float.isFinite(timeInTicks) || timeInTicks < 0.0F) {
				throw new IllegalArgumentException(
						"timeInTicks must be finite and non-negative");
			}
		}
	}

	@FunctionalInterface
	public interface PlayerAnimationProvider {
		@Nullable
		PlayerAnimationState resolve(Player player, float partialTick);
	}

	private record Entry(
			ResourceLocation id,
			int priority,
			PlayerAnimationProvider provider) {}

	private AddonPlayerAnimations() {}
}

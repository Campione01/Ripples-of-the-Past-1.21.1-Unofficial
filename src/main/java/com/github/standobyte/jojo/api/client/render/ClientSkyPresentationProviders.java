package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed presentation policies for the vanilla client sky.
 *
 * <p>The first active provider wins in registration order. Policies only
 * transform vanilla sky values; they do not mutate level time, game rules,
 * framebuffers, shaders, or third-party renderer state. Resolution is
 * stateless, so resource reload and disconnect require no retained-world
 * cleanup.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkyPresentationProviders {
	private static final Map<ResourceLocation,
			ClientSkyPresentationProvider> PROVIDERS =
					new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private ClientSkyPresentationProviders() {}

	public static synchronized void register(
			ResourceLocation owner,
			ClientSkyPresentationProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate client sky presentation provider: "
							+ owner);
		}
		publishSnapshot();
	}

	public static float timeOfDay(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.timeOfDay(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "time of day", error);
			return original;
		}
	}

	public static float skyDarken(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.skyDarken(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky darken", error);
			return original;
		}
	}

	public static float starBrightness(
			ClientLevel level,
			float partialTick,
			float original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return resolved.presentation()
					.starBrightness(original, partialTick);
		}
		catch (RuntimeException error) {
			logFailure(
					resolved.owner(),
					"star brightness",
					error);
			return original;
		}
	}

	public static Vec3 skyColor(
			ClientLevel level,
			Vec3 cameraPosition,
			float partialTick,
			Vec3 original) {
		ResolvedPresentation resolved = resolve(level);
		if (resolved == null) {
			return original;
		}
		try {
			return Objects.requireNonNull(
					resolved.presentation().skyColor(
							original,
							cameraPosition,
							partialTick),
					"sky color");
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky color", error);
			return original;
		}
	}

	@Nullable
	private static ResolvedPresentation resolve(
			ClientLevel level) {
		ClientSkyPresentationQuery query =
				new ClientSkyPresentationQuery(level);
		for (Registration registration : snapshot) {
			try {
				ClientSkyPresentation presentation =
						registration.provider().presentation(query);
				if (presentation != null) {
					return new ResolvedPresentation(
							registration.owner(),
							presentation);
				}
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"policy resolution",
						error);
			}
		}
		return null;
	}

	private static void logFailure(
			ResourceLocation owner,
			String phase,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Client sky presentation provider {} failed during {}.",
				owner,
				phase,
				error);
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
			ClientSkyPresentationProvider provider) {}

	private record ResolvedPresentation(
			ResourceLocation owner,
			ClientSkyPresentation presentation) {}
}

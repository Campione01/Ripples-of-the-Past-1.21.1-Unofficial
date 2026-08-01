package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed replacements for the vanilla client sky renderer.
 *
 * <p>The first active provider wins in registration order. Provider and
 * renderer failures are logged and fall back to the vanilla sky path.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSkyRenderers {
	private static final Map<ResourceLocation, ClientSkyRendererProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private ClientSkyRenderers() {}

	public static synchronized void register(
			ResourceLocation owner,
			ClientSkyRendererProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate client sky renderer provider: " + owner);
		}
		publishSnapshot();
	}

	@ApiStatus.Internal
	public static boolean renderSky(ClientSkyRenderContext context) {
		Objects.requireNonNull(context, "context");
		ResolvedRenderer resolved = resolve(context.level());
		if (resolved == null) {
			return false;
		}
		try {
			resolved.renderer().render(context);
			return true;
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "sky render", error);
			return false;
		}
	}

	@ApiStatus.Internal
	public static boolean suppressesClouds(ClientLevel level) {
		ResolvedRenderer resolved = resolve(level);
		if (resolved == null) {
			return false;
		}
		try {
			return resolved.renderer().suppressVanillaClouds();
		}
		catch (RuntimeException error) {
			logFailure(resolved.owner(), "cloud suppression", error);
			return false;
		}
	}

	@Nullable
	private static ResolvedRenderer resolve(ClientLevel level) {
		ClientSkyRendererQuery query = new ClientSkyRendererQuery(level);
		for (Registration registration : snapshot) {
			try {
				ClientSkyRenderer renderer =
						registration.provider().renderer(query);
				if (renderer != null) {
					return new ResolvedRenderer(
							registration.owner(), renderer);
				}
			}
			catch (RuntimeException error) {
				logFailure(
						registration.owner(),
						"provider resolution",
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
				"Client sky renderer provider {} failed during {}.",
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
			ClientSkyRendererProvider provider) {}

	private record ResolvedRenderer(
			ResourceLocation owner,
			ClientSkyRenderer renderer) {}
}

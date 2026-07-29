package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-only, owner-keyed base-model tint providers.
 *
 * <p>The first provider that returns a tint owns the base model for that
 * render call. Armor, feature layers, and other renderer passes are not
 * intercepted.</p>
 */
public final class LivingEntityBaseModelTints {
	private static final Map<ResourceLocation, LivingEntityBaseModelTintProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();

	private LivingEntityBaseModelTints() {}

	public static synchronized void register(
			ResourceLocation owner,
			LivingEntityBaseModelTintProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate living base-model tint provider: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((id, registered) ->
				registrations.add(new Registration(id, registered)));
		snapshot = List.copyOf(registrations);
	}

	@ApiStatus.Internal
	public static int apply(
			LivingEntity entity,
			EntityModel<?> model,
			float partialTick,
			int originalColor) {
		if (snapshot.isEmpty()) {
			return originalColor;
		}
		LivingEntityBaseModelTintQuery query =
				new LivingEntityBaseModelTintQuery(
						entity, model, partialTick, originalColor);
		for (Registration registration : snapshot) {
			try {
				OptionalInt tint = Objects.requireNonNull(
						registration.provider().baseModelTint(query),
						"base model tint");
				if (tint.isPresent()) {
					return tint.getAsInt();
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Living base-model tint provider {} failed.",
						registration.owner(),
						error);
			}
		}
		return originalColor;
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
			LivingEntityBaseModelTintProvider provider) {}
}

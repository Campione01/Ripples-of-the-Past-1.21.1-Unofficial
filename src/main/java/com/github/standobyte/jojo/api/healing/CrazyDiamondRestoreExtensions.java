package com.github.standobyte.jojo.api.healing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Owner-keyed Crazy Diamond restoration extensions.
 *
 * <p>Providers are evaluated in registration order. External restoration uses
 * the first handled result. Post-living observers all run and their additive
 * results are merged. Provider failures are isolated.</p>
 */
public final class CrazyDiamondRestoreExtensions {
	private static final Map<ResourceLocation,
			CrazyDiamondRestoreExtension> EXTENSIONS =
					new LinkedHashMap<>();

	private CrazyDiamondRestoreExtensions() {}

	public static synchronized void register(
			ResourceLocation owner,
			CrazyDiamondRestoreExtension extension) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(extension, "extension");
		if (EXTENSIONS.putIfAbsent(owner, extension) != null) {
			throw new IllegalStateException(
					"Duplicate Crazy Diamond restore extension: "
							+ owner);
		}
	}

	@ApiStatus.Internal
	public static boolean canTarget(Entity target) {
		Objects.requireNonNull(target, "target");
		for (Map.Entry<ResourceLocation,
				CrazyDiamondRestoreExtension> entry : snapshot()) {
			try {
				if (entry.getValue().canTarget(target)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				logFailure(entry.getKey(), "target matching", error);
			}
		}
		return false;
	}

	@ApiStatus.Internal
	public static ExternalRestoreResult restoreExternal(
			ExternalRestoreContext context) {
		Objects.requireNonNull(context, "context");
		for (Map.Entry<ResourceLocation,
				CrazyDiamondRestoreExtension> entry : snapshot()) {
			try {
				ExternalRestoreResult result =
						Objects.requireNonNull(
								entry.getValue()
										.restoreExternal(context),
								"extension result");
				if (result.handled()) {
					return result;
				}
			}
			catch (RuntimeException error) {
				logFailure(
						entry.getKey(),
						"external restoration",
						error);
			}
		}
		return ExternalRestoreResult.unhandled();
	}

	@ApiStatus.Internal
	public static RestoreAugmentation afterLivingRestoreAttempt(
			LivingRestoreContext context) {
		Objects.requireNonNull(context, "context");
		boolean healingActive = false;
		boolean barrageVisuals = false;
		float hpForExperience = 0;
		for (Map.Entry<ResourceLocation,
				CrazyDiamondRestoreExtension> entry : snapshot()) {
			try {
				RestoreAugmentation result =
						Objects.requireNonNull(
								entry.getValue()
										.afterLivingRestoreAttempt(
												context),
								"extension augmentation");
				healingActive |= result.healingActive();
				barrageVisuals |= result.barrageVisuals();
				hpForExperience += result.hpForExperience();
			}
			catch (RuntimeException error) {
				logFailure(
						entry.getKey(),
						"post-living restoration",
						error);
			}
		}
		return new RestoreAugmentation(
				healingActive,
				barrageVisuals,
				hpForExperience);
	}

	private static synchronized List<Map.Entry<ResourceLocation,
			CrazyDiamondRestoreExtension>> snapshot() {
		return new ArrayList<>(EXTENSIONS.entrySet());
	}

	private static void logFailure(
			ResourceLocation owner,
			String operation,
			RuntimeException error) {
		JojoMod.getLogger().error(
				"Crazy Diamond restore extension {} failed during {}.",
				owner,
				operation,
				error);
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(EXTENSIONS.keySet());
	}

	static synchronized void resetForTests() {
		EXTENSIONS.clear();
	}
}

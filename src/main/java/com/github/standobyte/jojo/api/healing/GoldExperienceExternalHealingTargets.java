package com.github.standobyte.jojo.api.healing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed external Gold Experience healing targets.
 *
 * <p>Handlers are queried in registration order and must be side-effect free.
 * The first valid resolution wins. Handler failures are isolated.</p>
 */
public final class GoldExperienceExternalHealingTargets {
	private static final Map<ResourceLocation,
			GoldExperienceExternalHealingTargetHandler> HANDLERS =
					new LinkedHashMap<>();

	private GoldExperienceExternalHealingTargets() {}

	public static synchronized void register(
			ResourceLocation owner,
			GoldExperienceExternalHealingTargetHandler handler) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(handler, "handler");
		if (HANDLERS.putIfAbsent(owner, handler) != null) {
			throw new IllegalStateException(
					"Duplicate Gold Experience external healing "
							+ "target handler: " + owner);
		}
	}

	@ApiStatus.Internal
	@Nullable
	public static GoldExperienceExternalHealingTarget resolve(
			Entity rawTarget,
			LivingEntity healer) {
		Objects.requireNonNull(rawTarget, "rawTarget");
		Objects.requireNonNull(healer, "healer");
		for (Map.Entry<ResourceLocation,
				GoldExperienceExternalHealingTargetHandler>
				entry : snapshot()) {
			try {
				GoldExperienceExternalHealingTarget result =
						entry.getValue().resolve(rawTarget, healer);
				if (result == null) {
					continue;
				}
				if (result.rawTarget() != rawTarget) {
					JojoMod.getLogger().error(
							"Gold Experience external healing "
									+ "target handler {} changed "
									+ "the raw target.",
							entry.getKey());
					continue;
				}
				return result;
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Gold Experience external healing target "
								+ "handler {} failed.",
						entry.getKey(),
						error);
			}
		}
		return null;
	}

	private static synchronized List<Map.Entry<ResourceLocation,
			GoldExperienceExternalHealingTargetHandler>> snapshot() {
		return new ArrayList<>(HANDLERS.entrySet());
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(HANDLERS.keySet());
	}

	static synchronized void resetForTests() {
		HANDLERS.clear();
	}
}

package com.github.standobyte.jojo.api.client.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed callbacks run after the core's final humanoid setup adjustment.
 */
@OnlyIn(Dist.CLIENT)
public final class HumanoidModelPostSetup {
	private static final Map<ResourceLocation,
			HumanoidModelPostSetupCallback> CALLBACKS =
					new LinkedHashMap<>();

	private HumanoidModelPostSetup() {}

	public static synchronized void register(
			ResourceLocation owner,
			HumanoidModelPostSetupCallback callback) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(callback, "callback");
		if (CALLBACKS.putIfAbsent(owner, callback) != null) {
			throw new IllegalStateException(
					"Duplicate humanoid post-setup callback: " + owner);
		}
	}

	@ApiStatus.Internal
	public static void apply(
			LivingEntity entity, HumanoidModel<?> model) {
		List<Map.Entry<ResourceLocation,
				HumanoidModelPostSetupCallback>> snapshot;
		synchronized (HumanoidModelPostSetup.class) {
			snapshot = new ArrayList<>(CALLBACKS.entrySet());
		}
		for (Map.Entry<ResourceLocation,
				HumanoidModelPostSetupCallback> entry : snapshot) {
			try {
				entry.getValue().apply(entity, model);
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Humanoid post-setup callback {} failed.",
						entry.getKey(),
						error);
			}
		}
	}

	static synchronized List<ResourceLocation> registeredOwners() {
		return List.copyOf(CALLBACKS.keySet());
	}

	static synchronized void resetForTests() {
		CALLBACKS.clear();
	}
}

package com.github.standobyte.jojo.api.client.render;

import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Computes observer-specific vanilla world render passes to suppress for one
 * {@code LevelRenderer.renderLevel} frame.
 */
@FunctionalInterface
public interface ObserverWorldRenderPolicyProvider {
	/**
	 * Returns the vanilla world passes to suppress for this observer and frame.
	 * Returning {@code null} is equivalent to returning an empty set.
	 */
	Set<Pass> suppressedPasses(Context context);

	enum Pass {
		TERRAIN,
		WEATHER,
		PARTICLES,
		BLOCK_ENTITIES
	}

	record Context(
			ClientLevel level,
			@Nullable LocalPlayer observer,
			@Nullable Entity cameraEntity,
			Camera camera,
			long gameTime,
			float partialTick) {
		public Context {
			Objects.requireNonNull(level, "level");
			Objects.requireNonNull(camera, "camera");
		}
	}
}

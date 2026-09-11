package com.github.standobyte.jojo.api.client.render;

import net.minecraft.world.phys.Vec3;

/**
 * Presentation-only transforms for the client sky and compatible shader sky uniforms.
 */
public interface ClientSkyPresentation {
	default float timeOfDay(float original, float partialTick) {
		return original;
	}

	default float skyDarken(float original, float partialTick) {
		return original;
	}

	default float starBrightness(float original, float partialTick) {
		return original;
	}

	default Vec3 skyColor(
			Vec3 original,
			Vec3 cameraPosition,
			float partialTick) {
		return original;
	}
}

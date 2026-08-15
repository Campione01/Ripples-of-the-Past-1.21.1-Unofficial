package com.github.standobyte.jojo.client.shader.core;

import javax.annotation.Nullable;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.renderer.PostChain;

public final class EntityOutlinePostChainCompat {
	private EntityOutlinePostChainCompat() {}

	public static State normalize(
			PostChain effect, @Nullable RenderTarget activeMainTarget,
			@Nullable State previous) {
		if (activeMainTarget == null
				|| activeMainTarget.width <= 0
				|| activeMainTarget.height <= 0) {
			return previous;
		}
		if (requiresResize(
				previous, effect, activeMainTarget,
				activeMainTarget.width, activeMainTarget.height)) {
			effect.screenTarget = activeMainTarget;
			effect.resize(activeMainTarget.width, activeMainTarget.height);
		}
		return new State(
				effect, activeMainTarget,
				activeMainTarget.width, activeMainTarget.height);
	}

	static boolean requiresResize(
			@Nullable State previous, Object effect, Object screenTarget,
			int width, int height) {
		return previous == null
				|| previous.effect() != effect
				|| previous.screenTarget() != screenTarget
				|| previous.width() != width
				|| previous.height() != height;
	}

	public record State(
			Object effect, Object screenTarget, int width, int height) {}
}

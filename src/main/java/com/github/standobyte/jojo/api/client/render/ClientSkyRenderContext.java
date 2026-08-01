package com.github.standobyte.jojo.api.client.render;

import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;

public record ClientSkyRenderContext(
		ClientLevel level,
		Matrix4f frustumMatrix,
		Matrix4f projectionMatrix,
		float partialTick,
		Camera camera,
		boolean foggy,
		Runnable skyFogSetup) {}

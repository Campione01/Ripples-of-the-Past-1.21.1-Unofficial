package com.github.standobyte.jojo.mixin.client.render;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ClientSkyPresentationProviders;
import com.github.standobyte.jojo.api.client.render.ClientSkyRenderContext;
import com.github.standobyte.jojo.api.client.render.ClientSkyRenderers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyPresentationMixin {
	@Shadow private ClientLevel level;

	@Inject(
			method = "renderSky(Lorg/joml/Matrix4f;"
					+ "Lorg/joml/Matrix4f;F"
					+ "Lnet/minecraft/client/Camera;Z"
					+ "Ljava/lang/Runnable;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$renderCustomSky(
			Matrix4f frustumMatrix,
			Matrix4f projectionMatrix,
			float partialTick,
			Camera camera,
			boolean foggy,
			Runnable skyFogSetup,
			CallbackInfo ci) {
		ClientLevel renderLevel = level;
		if (renderLevel != null && ClientSkyRenderers.renderSky(
				new ClientSkyRenderContext(
						renderLevel,
						frustumMatrix,
						projectionMatrix,
						partialTick,
						camera,
						foggy,
						skyFogSetup))) {
			ci.cancel();
		}
	}

	@Inject(
			method = "renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressVanillaClouds(
			PoseStack poseStack,
			Matrix4f frustumMatrix,
			Matrix4f projectionMatrix,
			float partialTick,
			double cameraX,
			double cameraY,
			double cameraZ,
			CallbackInfo ci) {
		ClientLevel renderLevel = level;
		if (renderLevel != null
				&& ClientSkyRenderers.suppressesClouds(renderLevel)) {
			ci.cancel();
		}
	}

	@WrapOperation(
			method = "renderSky",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/multiplayer/"
							+ "ClientLevel;getTimeOfDay(F)F"))
	private float jojo_ripples$presentSkyDomeTime(
			ClientLevel level,
			float partialTick,
			Operation<Float> original) {
		return ClientSkyPresentationProviders.timeOfDay(
				level,
				partialTick,
				original.call(level, partialTick));
	}
}

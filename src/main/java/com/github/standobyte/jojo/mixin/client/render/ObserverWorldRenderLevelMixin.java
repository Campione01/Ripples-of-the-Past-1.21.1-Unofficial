package com.github.standobyte.jojo.mixin.client.render;

import javax.annotation.Nullable;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicies;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicies.FrameScope;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Context;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;

@Mixin(LevelRenderer.class)
public abstract class ObserverWorldRenderLevelMixin {
	@Shadow @Final private Minecraft minecraft;
	@Shadow @Nullable private ClientLevel level;

	@WrapMethod(method = "renderLevel")
	private void jojo_ripples$withObserverWorldRenderPolicy(
			DeltaTracker deltaTracker,
			boolean renderBlockOutline,
			Camera camera,
			GameRenderer gameRenderer,
			LightTexture lightTexture,
			Matrix4f frustumMatrix,
			Matrix4f projectionMatrix,
			Operation<Void> original) {
		ClientLevel renderLevel = level;
		Context context = renderLevel != null
				? new Context(
						renderLevel,
						minecraft.player,
						camera.getEntity(),
						camera,
						renderLevel.getGameTime(),
						deltaTracker
								.getGameTimeDeltaPartialTick(false))
				: null;
		try (FrameScope ignored =
				ObserverWorldRenderPolicies.beginFrame(context)) {
			original.call(
					deltaTracker,
					renderBlockOutline,
					camera,
					gameRenderer,
					lightTexture,
					frustumMatrix,
					projectionMatrix);
		}
	}

	@Inject(
			method = "renderSectionLayer",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressTerrain(
			RenderType renderType,
			double cameraX,
			double cameraY,
			double cameraZ,
			Matrix4f frustumMatrix,
			Matrix4f projectionMatrix,
			CallbackInfo ci) {
		if (ObserverWorldRenderPolicies.suppresses(Pass.TERRAIN)) {
			ci.cancel();
		}
	}

	@Inject(
			method = "renderSnowAndRain",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressWeather(
			LightTexture lightTexture,
			float partialTick,
			double cameraX,
			double cameraY,
			double cameraZ,
			CallbackInfo ci) {
		if (ObserverWorldRenderPolicies.suppresses(Pass.WEATHER)) {
			ci.cancel();
		}
	}
}

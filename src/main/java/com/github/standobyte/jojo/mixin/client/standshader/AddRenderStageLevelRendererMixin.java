package com.github.standobyte.jojo.mixin.client.standshader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.client.shader.CustomLevelRenderStages;
import com.github.standobyte.jojo.client.shader.ModShaders;
import com.github.standobyte.jojo.client.shader.core.EntityOutlinePostChainCompat;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.PostChain;
import javax.annotation.Nullable;
import org.joml.Matrix4f;

@Mixin(LevelRenderer.class)
public abstract class AddRenderStageLevelRendererMixin {
	@Shadow @Nullable private PostChain entityEffect;

	@Unique
	private EntityOutlinePostChainCompat.State jojo_ripples$outlinePostChainState;

	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void jojo_ripples$beginStandTranslucencyFrame(DeltaTracker deltaTracker,
			boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture,
			Matrix4f projectionMatrix, Matrix4f frustumMatrix, CallbackInfo ci) {
		if (entityEffect != null) {
			jojo_ripples$outlinePostChainState =
					EntityOutlinePostChainCompat.normalize(
							entityEffect,
							Minecraft.getInstance().getMainRenderTarget(),
							jojo_ripples$outlinePostChainState);
		}
		ModShaders shaders = ModShaders.getInstance();
		if (shaders != null && shaders.standTranslucencyFramebuffer != null) {
			shaders.standTranslucencyFramebuffer.beginFrame();
		}
	}

	@Inject(method = "doEntityOutline", at = @At("TAIL"))
	private void jojo_ripples$beforeSpectatorShaderStage(CallbackInfo ci) {
		jojo_ripples$runBeforeSpectatorShaderStage();
	}

	private static void jojo_ripples$runBeforeSpectatorShaderStage() {
		ModShaders shaders = ModShaders.getInstance();
		if (shaders != null && CustomLevelRenderStages.BEFORE_SPECTATOR_SHADER != null) {
			shaders.frameRenderCallback(CustomLevelRenderStages.BEFORE_SPECTATOR_SHADER);
		}
	}
}

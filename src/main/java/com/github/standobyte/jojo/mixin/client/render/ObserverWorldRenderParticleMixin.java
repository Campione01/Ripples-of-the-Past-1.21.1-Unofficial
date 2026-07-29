package com.github.standobyte.jojo.mixin.client.render;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicies;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Pass;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;

@Mixin(ParticleEngine.class)
public abstract class ObserverWorldRenderParticleMixin {
	@Inject(
			method = "render(Lnet/minecraft/client/renderer/LightTexture;"
					+ "Lnet/minecraft/client/Camera;F"
					+ "Lnet/minecraft/client/renderer/culling/Frustum;"
					+ "Ljava/util/function/Predicate;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressParticleDraw(
			LightTexture lightTexture,
			Camera camera,
			float partialTick,
			@Nullable Frustum frustum,
			Predicate<ParticleRenderType> renderTypePredicate,
			CallbackInfo ci) {
		if (ObserverWorldRenderPolicies.suppresses(Pass.PARTICLES)) {
			ci.cancel();
		}
	}
}

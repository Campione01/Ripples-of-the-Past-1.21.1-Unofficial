package com.github.standobyte.jojo.mixin.client.hamon;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom.HamonAuraParticleRenderType;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;

@Mixin(ParticleEngine.class)
public class ParticleEngineHamonAuraMixin {
	@Inject(
			method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$restoreHamonAuraRenderStateAfterDraw(LightTexture lightTexture, Camera camera,
			float partialTick, @Nullable Frustum frustum, Predicate<ParticleRenderType> renderTypePredicate,
			CallbackInfo ci) {
		HamonAuraParticleRenderType.endAuraBatchIfOpen();
	}

	@Inject(
			method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
			at = @At("TAIL"))
	private void jojo_ripples$restoreHamonAuraRenderStateAtRenderEnd(LightTexture lightTexture, Camera camera,
			float partialTick, @Nullable Frustum frustum, Predicate<ParticleRenderType> renderTypePredicate,
			CallbackInfo ci) {
		HamonAuraParticleRenderType.endAuraBatchIfOpen();
	}
}

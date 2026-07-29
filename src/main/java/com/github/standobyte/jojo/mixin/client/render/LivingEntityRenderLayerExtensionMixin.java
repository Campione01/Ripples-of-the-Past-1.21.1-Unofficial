package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.api.client.render.LivingEntityRenderLayerExtensions;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderLayerExtensionMixin {
	@Shadow EntityModel<?> model;

	@Inject(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/"
							+ "PoseStack;popPose()V",
					shift = At.Shift.BEFORE))
	private void jojo_ripples$renderAddonLivingLayers(
			LivingEntity entity,
			float entityYaw,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci) {
		if (!EntityMaskPostEffect.isCapturePass()
				&& !entity.isSpectator()) {
			LivingEntityRenderLayerExtensions.renderAfterVanillaLayers(
					entity,
					model,
					poseStack,
					buffer,
					packedLight,
					partialTick);
		}
	}
}

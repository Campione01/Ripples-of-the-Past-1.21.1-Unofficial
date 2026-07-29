package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.github.standobyte.jojo.api.client.render.PlayerBaseModelVisibilityPolicies;
import com.github.standobyte.jojo.api.client.render.PlayerBaseModelVisibilityPolicies.FrameScope;
import com.github.standobyte.jojo.api.client.render.PlayerBaseModelVisibilityPolicies.RenderFrame;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerBaseModelVisibilityMixin {
	@Shadow protected EntityModel<?> model;

	@WrapMethod(method = "render")
	private void jojo_ripples$withPlayerVisibilityDecision(
			LivingEntity entity,
			float entityYaw,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			int light,
			Operation<Void> original) {
		if (!(entity instanceof AbstractClientPlayer player)
				|| !(model instanceof PlayerModel<?> playerModel)) {
			original.call(
					entity,
					entityYaw,
					partialTick,
					poseStack,
					bufferSource,
					light);
			return;
		}

		try (RenderFrame ignored =
				PlayerBaseModelVisibilityPolicies.enterRenderFrame(
						player, playerModel, partialTick)) {
			original.call(
					entity,
					entityYaw,
					partialTick,
					poseStack,
					bufferSource,
					light);
		}
	}

	@WrapOperation(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;"
							+ "renderToBuffer("
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
							+ "III)V"))
	private void jojo_ripples$withPlayerBaseModelVisibility(
			EntityModel<?> renderedModel,
			PoseStack modelPoseStack,
			VertexConsumer vertices,
			int packedLight,
			int packedOverlay,
			int color,
			Operation<Void> original,
			LivingEntity entity,
			float entityYaw,
			float partialTick,
			PoseStack renderPoseStack,
			MultiBufferSource bufferSource,
			int renderLight) {
		if (!(entity instanceof AbstractClientPlayer player)
				|| !(renderedModel instanceof PlayerModel<?> playerModel)) {
			original.call(
					renderedModel,
					modelPoseStack,
					vertices,
					packedLight,
					packedOverlay,
					color);
			return;
		}

		try (FrameScope ignored =
				PlayerBaseModelVisibilityPolicies.beginFrame(
						player, playerModel, partialTick)) {
			original.call(
					renderedModel,
					modelPoseStack,
					vertices,
					packedLight,
					packedOverlay,
					color);
		}
	}
}

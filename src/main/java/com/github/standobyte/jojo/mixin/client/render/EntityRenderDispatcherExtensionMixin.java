package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.api.client.render.EntityPostRenderExtensions;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherExtensionMixin {
	@Inject(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/"
							+ "EntityRenderer;render("
							+ "Lnet/minecraft/world/entity/Entity;FF"
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lnet/minecraft/client/renderer/"
							+ "MultiBufferSource;I)V",
					shift = At.Shift.AFTER))
	private <E extends Entity> void jojo_ripples$afterRegisteredRenderer(
			E entity,
			double x,
			double y,
			double z,
			float entityYaw,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource buffer,
			int packedLight,
			CallbackInfo ci) {
		if (EntityMaskPostEffect.isCapturePass()) {
			return;
		}
		EntityRenderer<? super E> renderer =
				((EntityRenderDispatcher) (Object) this)
						.getRenderer(entity);
		EntityPostRenderExtensions.afterEntityRender(
				entity,
				renderer,
				poseStack,
				buffer,
				packedLight,
				entityYaw,
				partialTick);
	}
}

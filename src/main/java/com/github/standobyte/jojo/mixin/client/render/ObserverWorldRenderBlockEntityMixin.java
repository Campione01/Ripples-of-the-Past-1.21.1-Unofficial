package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicies;
import com.github.standobyte.jojo.api.client.render.ObserverWorldRenderPolicyProvider.Pass;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class ObserverWorldRenderBlockEntityMixin {
	@Inject(
			method = "render(Lnet/minecraft/world/level/block/entity/"
					+ "BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/"
					+ "MultiBufferSource;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void jojo_ripples$suppressBlockEntityDraw(
			BlockEntity blockEntity,
			float partialTick,
			PoseStack poseStack,
			MultiBufferSource bufferSource,
			CallbackInfo ci) {
		if (ObserverWorldRenderPolicies
				.suppresses(Pass.BLOCK_ENTITIES)) {
			ci.cancel();
		}
	}
}

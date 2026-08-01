package com.github.standobyte.jojo.mixin.client.v1_21_1_modelanim;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.LivingEntityBaseModelTints;
import com.github.standobyte.jojo.api.client.render.LivingEntityMaterialTintPolicies;
import com.github.standobyte.jojo.client.entityanim.IHumanoidAnimModel;
import com.github.standobyte.v1_21_4_stuff.renderstate.EntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Shadow EntityModel<?> model;

	@ModifyVariable(
			method = "render",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 0)
	private MultiBufferSource jojo_ripples$wholeRenderMaterialTint(
			MultiBufferSource bufferSource,
			@Local(argsOnly = true) LivingEntity entity,
			@Local(argsOnly = true, ordinal = 1)
			float partialTick) {
		return LivingEntityMaterialTintPolicies.wrap(
				bufferSource, entity, partialTick);
	}

    @Inject(method = "render", at = @At(
	    		value = "INVOKE", 
	    		target = "Lnet/minecraft/client/model/EntityModel;setupAnim("
	    				+ "Lnet/minecraft/world/entity/Entity;FFFFF"
	    				+ ")V"))
	public void jojo_ripples$beforeVanillaAnimSetup(LivingEntity entity, float entityYaw, float partialTick, 
			PoseStack poseStack, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
		if (model instanceof IHumanoidAnimModel && RenderStateCrutches.currentEntityRenderState instanceof HumanoidRenderState) {
			EntityRenderState.resetPose(model);
		}
    }

    @Inject(method = "render", at = @At(
	    		value = "INVOKE", 
	    		target = "Lnet/minecraft/client/model/EntityModel;setupAnim("
	    				+ "Lnet/minecraft/world/entity/Entity;FFFFF"
	    				+ ")V",
				shift = Shift.AFTER))
	public void jojo_ripples$afterVanillaAnimSetup(LivingEntity entity, float entityYaw, float partialTick, 
			PoseStack poseStack, MultiBufferSource bufferSource, int light, CallbackInfo ci) {
		if (model instanceof IHumanoidAnimModel humanoidModel && RenderStateCrutches.currentEntityRenderState instanceof HumanoidRenderState humanoidRS) {
			humanoidModel.jojo_ripples$setupHumanoidAnim(humanoidRS);
		}
	}

	@ModifyArg(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;"
							+ "renderToBuffer("
							+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lcom/mojang/blaze3d/vertex/VertexConsumer;"
							+ "III)V"),
			index = 4)
	private int jojo_ripples$baseModelTint(
			int originalColor,
			@Local(argsOnly = true) LivingEntity entity,
			@Local(argsOnly = true, ordinal = 1)
			float partialTick) {
		return LivingEntityBaseModelTints.apply(
				entity, model, partialTick, originalColor);
	}

}

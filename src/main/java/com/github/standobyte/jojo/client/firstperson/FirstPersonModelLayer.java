package com.github.standobyte.jojo.client.firstperson;

import com.github.standobyte.jojo.client.entityanim.IHumanoidAnimModel;
import com.github.standobyte.jojo.client.entityrender.RipplesPlayerRenderState;
import com.github.standobyte.jojo.client.entityrender.RipplesPlayerRenderState.RipplesRenderStateExtensionMixin;
import com.github.standobyte.v1_21_4_stuff.renderstate.EntityRenderState;
import com.github.standobyte.v1_21_4_stuff.renderstate.HumanoidRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

// TODO (1.16.5) render the layers in 1st person when the player is invisible
public interface FirstPersonModelLayer {
	void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, 
			MultiBufferSource buffer, int light, LivingEntity entity, 
			LivingEntityRenderer<?, ?> entityRenderer, float partialTick);

	static void defaultRender(HumanoidArm side, PoseStack poseStack, 
			MultiBufferSource buffer, int light, LivingEntity entity, 
			LivingEntityRenderer<?, ?> entityRenderer, 
			HumanoidModel<?> model, ResourceLocation texture, float partialTick) {
		defaultRender(side, poseStack, buffer, light, entity, entityRenderer, model, texture,
				partialTick, 0xFFFFFFFF);
	}

	static void defaultRender(HumanoidArm side, PoseStack poseStack, 
			MultiBufferSource buffer, int light, LivingEntity entity, 
			LivingEntityRenderer<?, ?> entityRenderer, 
			HumanoidModel<?> model, ResourceLocation texture,
			float partialTick, int color) {
		if (texture == null || entity.isSpectator()) return;
		setupForFirstPersonRender(model, entity, partialTick);
		boolean ripplesAnimPlaying = model instanceof IHumanoidAnimModel animModel
				&& animModel.jojo_rippes$isPlayingAnimation();
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(texture));
		renderArmAndOuter(model, side, poseStack, vertexBuilder, light, OverlayTexture.NO_OVERLAY,
				color, ripplesAnimPlaying);
	}

	static boolean isRipplesAnimPlaying(HumanoidModel<?> model) {
		return model instanceof IHumanoidAnimModel animModel && animModel.jojo_rippes$isPlayingAnimation();
	}

	static void renderArmAndOuter(HumanoidModel<?> model, HumanoidArm side, PoseStack poseStack,
			VertexConsumer vertexBuilder, int light, int overlay, int color, boolean preserveCurrentPose) {
		ModelPart arm = getArm(model, side);
		if (!preserveCurrentPose) {
			arm.xRot = 0.0F;
		}
		arm.render(poseStack, vertexBuilder, light, overlay, color);

		if (model instanceof PlayerModel playerModel) {
			ModelPart armOuter = getArmOuter(playerModel, side);
			if (preserveCurrentPose) {
				armOuter.copyFrom(arm);
			}
			else {
				armOuter.xRot = 0.0F;
			}
			armOuter.render(poseStack, vertexBuilder, light, overlay, color);
		}
	}


	static ModelPart getArm(HumanoidModel<?> model, HumanoidArm side) {
		return side == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
	}

	static ModelPart getArmOuter(PlayerModel<?> model, HumanoidArm side) {
		return side == HumanoidArm.LEFT ? model.leftSleeve : model.rightSleeve;
	}

	@SuppressWarnings("unchecked")
	static <T extends LivingEntity> void setupForFirstPersonRender(HumanoidModel<T> model, LivingEntity entity, float partialTick) {
		model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		model.attackTime = 0.0F;
		model.crouching = false;
		model.swimAmount = 0.0F;
		resetForFirstPersonAnim(model);
		model.setupAnim((T) entity, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
		setupRipplesFirstPersonAnim(model, entity, partialTick);
	}

	static void resetForFirstPersonAnim(HumanoidModel<?> model) {
		if (model instanceof IHumanoidAnimModel) {
			EntityRenderState.resetPose(model);
		}
	}

	static void setupRipplesFirstPersonAnim(HumanoidModel<?> model, LivingEntity entity, float partialTick) {
		if (model instanceof IHumanoidAnimModel humanoidModel) {
			HumanoidRenderState renderState = new HumanoidRenderState();
			HumanoidRenderState.extractHumanoidRenderState(entity, renderState, partialTick);
			RipplesPlayerRenderState.extract(entity, renderState, ((RipplesRenderStateExtensionMixin) renderState).get(), partialTick);
			humanoidModel.jojo_ripples$setupHumanoidAnim(renderState);
		}
	}
}

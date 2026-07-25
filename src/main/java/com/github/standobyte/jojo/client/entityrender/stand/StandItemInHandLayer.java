package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.client.rendertype.AlphaMultiBufferSource;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class StandItemInHandLayer<
			T extends StandEntity,
			S extends StandEntityRenderState,
			M extends StandEntityModel<T, S>>
		extends RenderLayer<T, M> {
	private final ItemInHandRenderer itemInHandRenderer;

	public StandItemInHandLayer(RenderLayerParent<T, M> renderer, ItemInHandRenderer itemInHandRenderer) {
		super(renderer);
		this.itemInHandRenderer = itemInHandRenderer;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
			float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
			float netHeadYaw, float headPitch) {
		StandEntityRenderState renderState = RenderStateCrutches.currentStandEntityRenderState;
		if (renderState == null || renderState.alpha <= 0.0F
				|| renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}

		boolean rightHanded = entity.getMainArm() == HumanoidArm.RIGHT;
		ItemStack leftItem = rightHanded ? entity.getOffhandItem() : entity.getMainHandItem();
		ItemStack rightItem = rightHanded ? entity.getMainHandItem() : entity.getOffhandItem();
		boolean renderRight = HumanoidPart.contains(renderState.visibleParts, HumanoidPart.RIGHT_ARM) && !rightItem.isEmpty();
		boolean renderLeft = HumanoidPart.contains(renderState.visibleParts, HumanoidPart.LEFT_ARM) && !leftItem.isEmpty();
		if (!renderRight && !renderLeft) {
			return;
		}

		MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(buffer, renderState.alpha);
		poseStack.pushPose();
		if (getParentModel().young) {
			poseStack.translate(0.0F, 0.75F, 0.0F);
			poseStack.scale(0.5F, 0.5F, 0.5F);
		}
		if (renderRight) {
			renderArmWithItem(entity, rightItem, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
					HumanoidArm.RIGHT, poseStack, alphaBuffer, packedLight);
		}
		if (renderLeft) {
			renderArmWithItem(entity, leftItem, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
					HumanoidArm.LEFT, poseStack, alphaBuffer, packedLight);
		}
		poseStack.popPose();
	}

	private void renderArmWithItem(T entity, ItemStack itemStack, ItemDisplayContext displayContext,
			HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		getParentModel().translateToHand(arm, poseStack);
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		boolean leftArm = arm == HumanoidArm.LEFT;
		poseStack.translate((leftArm ? -1.0F : 1.0F) / 16.0F, 0.125F, -0.625F);
		itemInHandRenderer.renderItem(entity, itemStack, displayContext, leftArm, poseStack, buffer, packedLight);
		poseStack.popPose();
	}
}

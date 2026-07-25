package com.github.standobyte.jojo.subsystems.soul.client;

import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class SoulRenderer<T extends SoulEntity> extends EntityRenderer<T> {

	public SoulRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return null;
	}

	@Override
	public void render(T soulEntity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		if (!soulEntity.isInvisibleTo(Minecraft.getInstance().player)) {
			LivingEntity originEntity = soulEntity.getOriginEntity();
			if (originEntity != null) {
				renderSoul(originEntity, soulEntity, partialTick, poseStack, buffer, packedLight);
			}
			super.render(soulEntity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void renderSoul(LivingEntity originEntity, T soulEntity, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		EntityRenderer rendererRaw = entityRenderDispatcher.getRenderer(originEntity);
		if (!(rendererRaw instanceof LivingEntityRenderer livingRenderer)) {
			return;
		}
		EntityModel model = livingRenderer.getModel();

		poseStack.pushPose();
		float yHeadRotation = Mth.rotLerp(partialTick, soulEntity.yRotO, soulEntity.getYRot());
		float yBodyRotation = yHeadRotation;
		float xRotation = Mth.lerp(partialTick, soulEntity.xRotO, soulEntity.getXRot());
		float headBodyDiff = yHeadRotation - yBodyRotation;

		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRotation));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0D, -1.501F, 0.0D);

		model.prepareMobModel(originEntity, 0, 0, partialTick);
		model.setupAnim(originEntity, 0, 0, originEntity.tickCount + partialTick, headBodyDiff, xRotation);
		RenderType renderType = RenderType.itemEntityTranslucentCull(livingRenderer.getTextureLocation(originEntity));
		VertexConsumer vertexBuilder = buffer.getBuffer(renderType);
		int overlay = OverlayTexture.pack(OverlayTexture.u(0.5F), OverlayTexture.v(false));
		float lifeRatio = soulEntity.getInitialLifeSpan() > 0
				? Math.min((float) soulEntity.tickCount / (float) soulEntity.getInitialLifeSpan(), 1.0F)
				: 1.0F;
		float alpha = Math.min(0.75F, 3.0F * (1.0F - lifeRatio));
		model.renderToBuffer(poseStack, vertexBuilder, packedLight, overlay, 0xFFFF00 | ((int) (alpha * 255) << 24));

		poseStack.popPose();
	}
}

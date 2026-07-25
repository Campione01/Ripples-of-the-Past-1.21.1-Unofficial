package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class KnifeRenderer extends EntityRenderer<KnifeEntity> {
	public KnifeRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(KnifeEntity entity) {
		return entity.getKnifeTexture();
	}

	@Override
	public void render(KnifeEntity entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		float yRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot()) - 90.0F;
		poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
		poseStack.translate(0, 0.125, 0);
		poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTick, entity.xRotO, entity.getXRot())));
		float scale = 0.0375F;
		poseStack.scale(scale, scale, scale);
		poseStack.translate(1, 0, 0);

		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));
		vertex(poseStack, vertexBuilder, -4, -2, -2, 2F / 32F, 27F / 32F, -1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, -2, 2, 5F / 32F, 27F / 32F, -1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, 2, 2, 5F / 32F, 30F / 32F, -1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, 2, -2, 2F / 32F, 30F / 32F, -1, 0, 0, packedLight);

		vertex(poseStack, vertexBuilder, -4, 2, -2, 2F / 32F, 27F / 32F, 1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, 2, 2, 5F / 32F, 27F / 32F, 1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, -2, 2, 5F / 32F, 30F / 32F, 1, 0, 0, packedLight);
		vertex(poseStack, vertexBuilder, -4, -2, -2, 2F / 32F, 30F / 32F, 1, 0, 0, packedLight);

		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		vertex(poseStack, vertexBuilder, -8, -3, 0, 0, 12F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, -3, 0, 16F / 32F, 12F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, 3, 0, 16F / 32F, 21F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, -8, 3, 0, 0, 21F / 32F, 0, 1, 0, packedLight);

		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		vertex(poseStack, vertexBuilder, -8, -3, 0, 0, 0F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, -3, 0, 16F / 32F, 0F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, 3, 0, 16F / 32F, 9F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, -8, 3, 0, 0, 9F / 32F, 0, 1, 0, packedLight);

		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		vertex(poseStack, vertexBuilder, -8, -3, 0, 0, 21F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, -3, 0, 16F / 32F, 21F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, 3, 0, 16F / 32F, 12F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, -8, 3, 0, 0, 12F / 32F, 0, 1, 0, packedLight);

		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		vertex(poseStack, vertexBuilder, -8, -3, 0, 0, 9F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, -3, 0, 16F / 32F, 9F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, 8, 3, 0, 16F / 32F, 0F / 32F, 0, 1, 0, packedLight);
		vertex(poseStack, vertexBuilder, -8, 3, 0, 0, 0F / 32F, 0, 1, 0, packedLight);

		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder,
			float x, float y, float z, float u, float v,
			float normalX, float normalY, float normalZ, int packedLight) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, normalX, normalY, normalZ);
	}
}

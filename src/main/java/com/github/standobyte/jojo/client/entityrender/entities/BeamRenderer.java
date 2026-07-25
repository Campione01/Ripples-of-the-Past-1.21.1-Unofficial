package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.customobjects.entity_projectile.DamagingEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public abstract class BeamRenderer<T extends DamagingEntity> extends EntityRenderer<T> {
	public BeamRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	protected abstract Vec3 pointB(T entity, float partialTick);

	protected abstract float getBeamWidth(T entity);

	@Override
	public void render(T entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		packedLight = ClientUtil.MAX_LIGHT;
		Vec3 beamVec = pointB(entity, partialTick).subtract(entity.getPosition(partialTick));
		if (beamVec.lengthSqr() < 1.0E-6D) {
			poseStack.popPose();
			return;
		}

		float yRot = MathUtil.yRotDegFromVec(beamVec);
		float xRot = MathUtil.xRotDegFromVec(beamVec);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F - yRot));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-xRot));
		float beamWidth = getBeamWidth(entity);
		poseStack.scale(1.0F, beamWidth, beamWidth);
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity)));

		float length = (float) beamVec.length();
		int lengthInt = (int) length / 2;
		poseStack.translate(length / 2, 0.0D, 0.0D);
		float beamLength = length / 64F;
		poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
		for (int j = 0; j < 4; ++j) {
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
			vertex(poseStack, vertexBuilder, packedLight, -lengthInt, -1, 0, 0.0F, 0.0F, 0, 1, 0);
			vertex(poseStack, vertexBuilder, packedLight, lengthInt, -1, 0, beamLength, 0.0F, 0, 1, 0);
			vertex(poseStack, vertexBuilder, packedLight, lengthInt, 1, 0, beamLength, 0.046875F, 0, 1, 0);
			vertex(poseStack, vertexBuilder, packedLight, -lengthInt, 1, 0, 0.0F, 0.046875F, 0, 1, 0);
		}

		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public abstract ResourceLocation getTextureLocation(T entity);

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight,
			float x, float y, float z, float u, float v,
			float normalX, float normalY, float normalZ) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, normalX, normalY, normalZ);
	}
}

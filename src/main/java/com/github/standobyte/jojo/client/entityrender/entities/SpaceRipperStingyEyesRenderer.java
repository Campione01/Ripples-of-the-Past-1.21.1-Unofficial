package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;

public class SpaceRipperStingyEyesRenderer<T extends SpaceRipperStingyEyesEntity> extends EntityRenderer<T> {
	private static final ResourceLocation BEAM_TEX = JojoMod.resLoc("textures/entity/projectiles/space_ripper_stingy_eyes.png");

	public SpaceRipperStingyEyesRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return BEAM_TEX;
	}

	@Override
	public void render(T entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		packedLight = ClientUtil.MAX_LIGHT;
		Vec3 beamVec = entity.getOriginPoint(partialTick).subtract(entity.getPosition(partialTick));
		float yRot = MathUtil.yRotDegFromVec(beamVec);
		float xRot = MathUtil.xRotDegFromVec(beamVec);
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F - yRot));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-xRot));
		poseStack.scale(1.0F, 0.15F, 0.15F);

		Matrix3f lighting = poseStack.last().normal();
		lighting.identity();
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		lighting.rotate(Axis.XP.rotationDegrees(camera.getXRot()));
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucentCull(getTextureLocation(entity)));
		float length = (float) beamVec.length();

		renderSide(poseStack, 0.0F, -1.0F, 0.0F, length, vertexBuilder, packedLight);
		renderSide(poseStack, 0.0F, 0.0F, -1.0F, length, vertexBuilder, packedLight);
		renderSide(poseStack, 0.0F, 1.0F, 0.0F, length, vertexBuilder, packedLight);
		renderSide(poseStack, 0.0F, 0.0F, 1.0F, length, vertexBuilder, packedLight);

		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	private static void renderSide(PoseStack poseStack, float normalX, float normalY, float normalZ,
			float length, VertexConsumer vertexBuilder, int packedLight) {
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.0F, 0.125F);

		PoseStack.Pose pose = poseStack.last();
		vertex(pose, vertexBuilder, packedLight, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, normalX, normalY, normalZ);
		vertex(pose, vertexBuilder, packedLight, length, -1.0F, 0.0F, length, 0.0F, normalX, normalY, normalZ);
		vertex(pose, vertexBuilder, packedLight, length, 1.0F, 0.0F, length, 1.0F, normalX, normalY, normalZ);
		vertex(pose, vertexBuilder, packedLight, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, normalX, normalY, normalZ);

		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		vertex(pose, vertexBuilder, packedLight, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -normalX, -normalY, -normalZ);
		vertex(pose, vertexBuilder, packedLight, length, -1.0F, 0.0F, length, 0.0F, -normalX, -normalY, -normalZ);
		vertex(pose, vertexBuilder, packedLight, length, 1.0F, 0.0F, length, 1.0F, -normalX, -normalY, -normalZ);
		vertex(pose, vertexBuilder, packedLight, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, -normalX, -normalY, -normalZ);
		poseStack.popPose();
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer vertexBuilder, int packedLight,
			float x, float y, float z, float u, float v,
			float normalX, float normalY, float normalZ) {
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, normalX, normalY, normalZ);
	}
}

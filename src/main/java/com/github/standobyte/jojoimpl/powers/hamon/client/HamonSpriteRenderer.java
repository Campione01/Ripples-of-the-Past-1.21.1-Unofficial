package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

public class HamonSpriteRenderer<T extends Entity> extends EntityRenderer<T> {
	private final ResourceLocation texture;
	private final float scale;

	public HamonSpriteRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float scale) {
		super(context);
		this.texture = texture;
		this.scale = scale;
	}

	@Override
	public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
		poseStack.scale(scale, scale, scale);
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(texture));
		vertex(poseStack, vertexBuilder, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F);
		vertex(poseStack, vertexBuilder, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F);
		vertex(poseStack, vertexBuilder, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F);
		vertex(poseStack, vertexBuilder, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F);
		poseStack.popPose();
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, float x, float y, float z, float u, float v) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(ClientUtil.MAX_LIGHT)
				.setNormal(pose, 0, 1, 0);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return texture;
	}
}

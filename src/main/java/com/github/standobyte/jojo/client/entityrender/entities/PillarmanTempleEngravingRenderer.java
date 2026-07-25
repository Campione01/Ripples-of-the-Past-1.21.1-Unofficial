package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.worldgen.structure.PillarmanTempleEngravingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class PillarmanTempleEngravingRenderer extends EntityRenderer<PillarmanTempleEngravingEntity> {
	private static final ResourceLocation[] TEXTURES = {
			JojoMod.resLoc("textures/engraving/engraving_1.png"),
			JojoMod.resLoc("textures/engraving/engraving_2.png"),
			JojoMod.resLoc("textures/engraving/engraving_3.png"),
			JojoMod.resLoc("textures/engraving/engraving_4.png"),
			JojoMod.resLoc("textures/engraving/engraving_5.png"),
			JojoMod.resLoc("textures/engraving/engraving_6.png"),
			JojoMod.resLoc("textures/engraving/engraving_7.png"),
			JojoMod.resLoc("textures/engraving/engraving_8.png")
	};

	public PillarmanTempleEngravingRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(PillarmanTempleEngravingEntity entity, float yRotation, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yRotation));
		poseStack.scale(0.0625F, 0.0625F, 0.0625F);
		VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity)));
		renderEngraving(poseStack, vertexBuilder, entity);
		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(PillarmanTempleEngravingEntity entity) {
		int textureId = entity.getTextureId();
		if (textureId < 0 || textureId >= TEXTURES.length) {
			textureId = 0;
		}
		return TEXTURES[textureId];
	}

	private static void renderEngraving(PoseStack poseStack, VertexConsumer vertexBuilder,
			PillarmanTempleEngravingEntity entity) {
		int width = PillarmanTempleEngravingEntity.ENGRAVING_WIDTH;
		int height = PillarmanTempleEngravingEntity.ENGRAVING_HEIGHT;
		float left = -width / 2.0F;
		float bottom = -height / 2.0F;
		int widthBlocks = width / 16;
		int heightBlocks = height / 16;
		double textureWidth = 16.0D / widthBlocks;
		double textureHeight = 16.0D / heightBlocks;

		for (int blockX = 0; blockX < widthBlocks; blockX++) {
			for (int blockY = 0; blockY < heightBlocks; blockY++) {
				float rightX = left + (blockX + 1) * 16;
				float leftX = left + blockX * 16;
				float topY = bottom + (blockY + 1) * 16;
				float bottomY = bottom + blockY * 16;
				int lightX = Mth.floor(entity.getX());
				int lightY = Mth.floor(entity.getY() + (topY + bottomY) / 32.0F);
				int lightZ = Mth.floor(entity.getZ());
				Direction direction = entity.getDirection();
				if (direction == Direction.NORTH) {
					lightX = Mth.floor(entity.getX() + (rightX + leftX) / 32.0F);
				}
				else if (direction == Direction.WEST) {
					lightZ = Mth.floor(entity.getZ() - (rightX + leftX) / 32.0F);
				}
				else if (direction == Direction.SOUTH) {
					lightX = Mth.floor(entity.getX() - (rightX + leftX) / 32.0F);
				}
				else if (direction == Direction.EAST) {
					lightZ = Mth.floor(entity.getZ() + (rightX + leftX) / 32.0F);
				}
				int light = LevelRenderer.getLightColor(entity.level(), new BlockPos(lightX, lightY, lightZ));

				float u0 = (float) (0.0625D * textureWidth * (widthBlocks - blockX));
				float u1 = (float) (0.0625D * textureWidth * (widthBlocks - blockX - 1));
				float v0 = (float) (0.0625D * textureHeight * (heightBlocks - blockY));
				float v1 = (float) (0.0625D * textureHeight * (heightBlocks - blockY - 1));
				vertex(poseStack, vertexBuilder, rightX, bottomY, -0.5F, u1, v0, light);
				vertex(poseStack, vertexBuilder, leftX, bottomY, -0.5F, u0, v0, light);
				vertex(poseStack, vertexBuilder, leftX, topY, -0.5F, u0, v1, light);
				vertex(poseStack, vertexBuilder, rightX, topY, -0.5F, u1, v1, light);
			}
		}
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder,
			float x, float y, float z, float u, float v, int packedLight) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xBFBFBFBF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, 0.0F, 0.0F, -1.0F);
	}
}

package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.customobjects.entity_projectile.MolotovEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class MolotovRenderer extends ThrownItemRenderer<MolotovEntity> {
	private static final ResourceLocation FIRE_0 = ResourceLocation.withDefaultNamespace("block/fire_0");
	private static final ResourceLocation FIRE_1 = ResourceLocation.withDefaultNamespace("block/fire_1");

	public MolotovRenderer(EntityRendererProvider.Context context) {
		super(context, 1.0F, true);
	}

	@Override
	public void render(MolotovEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
		if (!entity.isInWaterOrRain()) {
			renderFireOverlay(entity, poseStack, buffer.getBuffer(Sheets.translucentCullBlockSheet()));
		}
	}

	private static void renderFireOverlay(MolotovEntity entity, PoseStack poseStack, VertexConsumer vertexBuilder) {
		poseStack.pushPose();
		TextureAtlasSprite fire0 = fireSprite(FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(FIRE_1);
		float scale = entity.getBbWidth();
		poseStack.scale(scale, scale, scale);
		poseStack.mulPose(Axis.YP.rotationDegrees(-Minecraft.getInstance().getEntityRenderDispatcher().camera.getYRot()));
		poseStack.translate(0.0D, 0.5D, -0.3D + entity.getBbHeight() / scale * 0.02D);

		float halfWidth = 0.5F;
		float heightLeft = entity.getBbHeight() / scale;
		float yOffset = 0.0F;
		float zOffset = 0.0F;
		for (int i = 0; heightLeft > 0.0F; i++) {
			TextureAtlasSprite sprite = i % 2 == 0 ? fire0 : fire1;
			float u0 = sprite.getU0();
			float v0 = sprite.getV0();
			float u1 = sprite.getU1();
			float v1 = sprite.getV1();
			if (i / 2 % 2 == 0) {
				float tmp = u1;
				u1 = u0;
				u0 = tmp;
			}
			vertex(poseStack, vertexBuilder, halfWidth, -yOffset, zOffset, u1, v1);
			vertex(poseStack, vertexBuilder, -halfWidth, -yOffset, zOffset, u0, v1);
			vertex(poseStack, vertexBuilder, -halfWidth, 1.4F - yOffset, zOffset, u0, v0);
			vertex(poseStack, vertexBuilder, halfWidth, 1.4F - yOffset, zOffset, u1, v0);
			heightLeft -= 0.45F;
			yOffset -= 0.45F;
			halfWidth *= 0.9F;
			zOffset += 0.03F;
		}
		poseStack.popPose();
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, float x, float y, float z, float u, float v) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(240)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}

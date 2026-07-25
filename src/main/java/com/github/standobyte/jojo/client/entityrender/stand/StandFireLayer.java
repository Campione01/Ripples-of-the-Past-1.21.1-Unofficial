package com.github.standobyte.jojo.client.entityrender.stand;

import org.joml.Quaternionf;

import com.github.standobyte.jojo.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import com.github.standobyte.jojo.client.rendertype.AlphaMultiBufferSource;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;

public final class StandFireLayer {
	public void render(PoseStack poseStack, MultiBufferSource buffer, StandEntity entity,
			StandEntityRenderState renderState, boolean displayFire, Quaternionf cameraOrientation) {
		if (!displayFire || renderState.alpha <= 0.0F
				|| renderState.obstructionRenderMode != ObstructionRenderMode.NONE) {
			return;
		}

		TextureAtlasSprite fire0 = ModelBakery.FIRE_0.sprite();
		TextureAtlasSprite fire1 = ModelBakery.FIRE_1.sprite();
		poseStack.pushPose();
		float scale = entity.getBbWidth() * 1.4F;
		poseStack.scale(scale, scale, scale);
		float halfWidth = 0.5F;
		float heightLeft = entity.getBbHeight() / scale;
		float yOffset = 0.0F;
		poseStack.mulPose(cameraOrientation);
		poseStack.translate(0.0F, 0.0F, 0.3F - (int) heightLeft * 0.02F);
		float zOffset = 0.0F;
		MultiBufferSource alphaBuffer = AlphaMultiBufferSource.wrap(buffer, renderState.alpha);
		VertexConsumer vertexConsumer = alphaBuffer.getBuffer(ModRenderTypes.standTranslucent(TextureAtlas.LOCATION_BLOCKS));

		for (int i = 0; heightLeft > 0.0F; i++) {
			TextureAtlasSprite sprite = i % 2 == 0 ? fire0 : fire1;
			float u0 = sprite.getU0();
			float v0 = sprite.getV0();
			float u1 = sprite.getU1();
			float v1 = sprite.getV1();
			if (i / 2 % 2 == 0) {
				float swap = u1;
				u1 = u0;
				u0 = swap;
			}

			PoseStack.Pose pose = poseStack.last();
			fireVertex(pose, vertexConsumer, -halfWidth, -yOffset, zOffset, u1, v1);
			fireVertex(pose, vertexConsumer, halfWidth, -yOffset, zOffset, u0, v1);
			fireVertex(pose, vertexConsumer, halfWidth, 1.4F - yOffset, zOffset, u0, v0);
			fireVertex(pose, vertexConsumer, -halfWidth, 1.4F - yOffset, zOffset, u1, v0);
			heightLeft -= 0.45F;
			yOffset -= 0.45F;
			halfWidth *= 0.9F;
			zOffset -= 0.03F;
		}

		poseStack.popPose();
	}

	private static void fireVertex(PoseStack.Pose pose, VertexConsumer buffer,
			float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setUv1(0, 10)
				.setLight(240)
				.setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}

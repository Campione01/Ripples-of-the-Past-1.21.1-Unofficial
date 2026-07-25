package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.client.entityrender.ModelPartWithName;
import com.github.standobyte.jojo.client.entityrender.ModelWithExtraFeatures;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.v1_21_4_stuff.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class MagiciansRedFlameLayer<
				T extends StandEntity,
				S extends StandEntityRenderState,
				M extends StandEntityModel<T, S>>
		extends RenderLayer<T, M> implements StandSkinUiLayer {
	private static final ResourceLocation MR_FIRE_0 = JojoMod.resLoc("block/mr_fire_0");
	private static final ResourceLocation MR_FIRE_1 = JojoMod.resLoc("block/mr_fire_1");
	private static final AttachedFlame[] FLAMES = {
			new AttachedFlame("left_arm_bend", 3, 4, 3),
			new AttachedFlame("right_arm_bend", 3, 4, 3),
			new AttachedFlame("left_leg", 5, 4, 4),
			new AttachedFlame("left_leg_bend", 4, 4, 4),
			new AttachedFlame("right_leg", 5, 4, 4),
			new AttachedFlame("right_leg_bend", 4, 4, 4)
	};

	public MagiciansRedFlameLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
			float walkAnimPos, float walkAnimSpeed, float partialTick,
			float ticks, float headYRotation, float headXRotation) {
		StandEntityRenderState renderState = RenderStateCrutches.currentStandEntityRenderState;
		if (renderState == null || !JojoMod.resLoc("magicians_red").equals(entity.getStandType()) || entity.isInWaterOrRain()) {
			return;
		}
		renderFlames(poseStack, buffer, renderState);
	}

	@Override
	public void renderForStandSkinUI(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState) {
		if (renderState == null || renderState.skin == null || !JojoMod.resLoc("magicians_red").equals(renderState.skin.standTypeId)) {
			return;
		}
		renderFlames(poseStack, buffer, renderState);
	}

	private void renderFlames(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState) {
		if (renderState.alpha <= 0.0F || renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}
		renderFlames(poseStack, buffer.getBuffer(ModRenderTypes.standTranslucent(InventoryMenu.BLOCK_ATLAS)),
				renderState, flameColor(renderState));
	}

	void renderClassicOutline(PoseStack poseStack, MultiBufferSource buffer, T entity,
			StandEntityRenderState renderState) {
		if (renderState == null || !JojoMod.resLoc("magicians_red").equals(entity.getStandType())
				|| entity.isInWaterOrRain()
				|| renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}
		renderFlames(poseStack, buffer.getBuffer(RenderType.outline(InventoryMenu.BLOCK_ATLAS)), renderState, -1);
	}

	private void renderFlames(PoseStack poseStack, VertexConsumer vertexBuilder,
			StandEntityRenderState renderState, int color) {
		TextureAtlasSprite fire0 = fireSprite(MR_FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(MR_FIRE_1);
		for (AttachedFlame flame : FLAMES) {
			renderAttachedFlame(poseStack, vertexBuilder, flame, fire0, fire1, color);
		}
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}

	private void renderAttachedFlame(PoseStack poseStack, VertexConsumer vertexBuilder, AttachedFlame flame,
			TextureAtlasSprite fire0, TextureAtlasSprite fire1, int color) {
		ModelPartWithName[] modelPath = ((ModelWithExtraFeatures) getParentModel()).jojo_ripples$getPathToModelPart(flame.partName);
		if (modelPath == null || !isModelPathVisible(modelPath)) {
			return;
		}

		poseStack.pushPose();
		for (ModelPartWithName modelPart : modelPath) {
			modelPart.part().translateAndRotate(poseStack);
		}
		renderFlame(poseStack, vertexBuilder, flame, fire0, fire1, color);
		poseStack.popPose();
	}

	private static boolean isModelPathVisible(ModelPartWithName[] modelPath) {
		for (ModelPartWithName modelPart : modelPath) {
			if (!modelPart.part().visible) {
				return false;
			}
		}
		return true;
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void renderFlame(PoseStack poseStack, VertexConsumer vertexBuilder, AttachedFlame flame,
			TextureAtlasSprite fire0, TextureAtlasSprite fire1, int color) {
		poseStack.pushPose();
		poseStack.translate(0, flame.bottomY, 0);
		poseStack.mulPose(Axis.XP.rotationDegrees(180));

		for (int yRot = 0; yRot < 4; yRot++) {
			poseStack.pushPose();
			float flameScale = flame.width * 1.4F;
			poseStack.scale(flameScale, flameScale, flameScale);
			float xHalfWidth = 0.5F;
			float xOffset = 0.0F;
			float heightLeft = flame.height / flameScale;
			float yOffset = 0.0F;
			poseStack.mulPose(Axis.YP.rotation(yRot * Mth.HALF_PI));
			poseStack.translate(0.0D, 0.0D, -0.4F + (int) heightLeft * 0.02F);
			float zOffset = 0.0F;
			for (int i = 0; heightLeft > 0.0F; i++) {
				TextureAtlasSprite sprite = i % 2 == 0 ? fire0 : fire1;
				float texU0 = sprite.getU0();
				float texV0 = sprite.getV0();
				float texU1 = sprite.getU1();
				float texV1 = sprite.getV1();
				if (i / 2 % 2 == 0) {
					float tmp = texU1;
					texU1 = texU0;
					texU0 = tmp;
				}
				vertex(poseStack, vertexBuilder, xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU1, texV1, color);
				vertex(poseStack, vertexBuilder, -xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU0, texV1, color);
				vertex(poseStack, vertexBuilder, -xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU0, texV0, color);
				vertex(poseStack, vertexBuilder, xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU1, texV0, color);
				heightLeft -= 0.45F;
				yOffset -= 0.45F;
				xHalfWidth *= 0.9F;
				zOffset += 0.03F;
			}
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static int flameColor(StandEntityRenderState renderState) {
		return FastColor.ARGB32.color(FastColor.as8BitChannel(renderState.alpha), 0xFFFFFFFF);
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, float x, float y, float z, float u, float v, int color) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(color)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(ClientUtil.MAX_LIGHT)
				.setNormal(pose, 0, 1, 0);
	}

	private static class AttachedFlame {
		private final String partName;
		private final float bottomY;
		private final float width;
		private final float height;

		private AttachedFlame(String partName, float bottomY, float width, float height) {
			this.partName = partName;
			this.bottomY = bottomY / 16F;
			this.width = width / 16F;
			this.height = height / 16F;
		}
	}
}

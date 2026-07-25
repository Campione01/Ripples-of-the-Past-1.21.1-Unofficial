package com.github.standobyte.jojo.client.entityrender.stand;

import com.github.standobyte.jojo.client.entityrender.ModelPartWithName;
import com.github.standobyte.jojo.client.entityrender.ModelWithExtraFeatures;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class SilverChariotRapierFlameLayer<
				T extends StandEntity,
				S extends StandEntityRenderState,
				M extends StandEntityModel<T, S>>
		extends RenderLayer<T, M> {
	private static final ResourceLocation FIRE_0 = ResourceLocation.withDefaultNamespace("block/fire_0");
	private static final ResourceLocation FIRE_1 = ResourceLocation.withDefaultNamespace("block/fire_1");
	private static final Flame[] RAPIER_FLAMES = {
			new Flame(0, 0, -3.0F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -4.5F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -6.0F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -7.5F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -9.0F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -10.5F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -12.0F, 1, 3, Direction.NORTH),
			new Flame(0, 0, -13.5F, 1, 3, Direction.NORTH)
	};

	public SilverChariotRapierFlameLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
			float walkAnimPos, float walkAnimSpeed, float partialTick,
			float ticks, float headYRotation, float headXRotation) {
		StandEntityRenderState renderState = RenderStateCrutches.currentStandEntityRenderState;
		if (renderState == null || renderState.alpha <= 0.0F
				|| !renderState.silverChariotRapierVisible || !renderState.silverChariotRapierOnFire) {
			return;
		}
		if (renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}
		renderRapierFlameLayer(poseStack,
				buffer.getBuffer(ModRenderTypes.standTranslucent(InventoryMenu.BLOCK_ATLAS)),
				renderState, flameColor(renderState));
	}

	void renderClassicOutline(PoseStack poseStack, MultiBufferSource buffer, StandEntityRenderState renderState) {
		if (renderState == null || !renderState.silverChariotRapierVisible || !renderState.silverChariotRapierOnFire
				|| renderState.isInvisibleToPlayer && !renderState.visibleForSpectator) {
			return;
		}
		renderRapierFlameLayer(poseStack, buffer.getBuffer(RenderType.outline(InventoryMenu.BLOCK_ATLAS)), renderState, -1);
	}

	private void renderRapierFlameLayer(PoseStack poseStack, VertexConsumer vertexBuilder,
			StandEntityRenderState renderState, int color) {

		ModelPartWithName[] rapierPath = ((ModelWithExtraFeatures) getParentModel()).jojo_ripples$getPathToModelPart("rapier");
		if (rapierPath == null || !isModelPathVisible(rapierPath)) {
			return;
		}

		poseStack.pushPose();
		for (ModelPartWithName modelPart : rapierPath) {
			modelPart.part().translateAndRotate(poseStack);
		}
		renderRapierFlames(poseStack, vertexBuilder, color);
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

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}

	private static void renderRapierFlames(PoseStack poseStack, VertexConsumer vertexBuilder, int color) {
		TextureAtlasSprite fire0 = fireSprite(FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(FIRE_1);
		for (Flame flame : RAPIER_FLAMES) {
			renderFlame(poseStack, vertexBuilder, flame, fire0, fire1, color);
		}
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void renderFlame(PoseStack poseStack, VertexConsumer vertexBuilder, Flame flame,
			TextureAtlasSprite fire0, TextureAtlasSprite fire1, int color) {
		poseStack.pushPose();
		poseStack.translate(flame.x, flame.y, flame.z);
		switch (flame.flameDirection) {
		case UP:
			poseStack.mulPose(Axis.XP.rotationDegrees(180));
			break;
		case NORTH:
			poseStack.mulPose(Axis.XN.rotationDegrees(90));
			break;
		case EAST:
			poseStack.mulPose(Axis.ZP.rotationDegrees(90));
			break;
		case SOUTH:
			poseStack.mulPose(Axis.XP.rotationDegrees(90));
			break;
		case WEST:
			poseStack.mulPose(Axis.ZN.rotationDegrees(90));
			break;
		case DOWN:
		default:
			break;
		}

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

	private static class Flame {
		private final double x;
		private final double y;
		private final double z;
		private final float width;
		private final float height;
		private final Direction flameDirection;

		private Flame(float x, float y, float z, float width, float height, Direction flameDirection) {
			this.x = x / 16F;
			this.y = y / 16F;
			this.z = z / 16F;
			this.width = width / 16F;
			this.height = height / 16F;
			this.flameDirection = flameDirection;
		}
	}
}

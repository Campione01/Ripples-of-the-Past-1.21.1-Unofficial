package com.github.standobyte.jojoimpl.stands.magiciansred.client;

import java.util.ArrayList;
import java.util.List;

import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRCrossfireHurricaneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class MRCrossfireHurricaneRenderer extends EntityRenderer<MRCrossfireHurricaneEntity> {
	private static final ResourceLocation MR_FIRE_0 = JojoMod.resLoc("block/mr_fire_0");
	private static final ResourceLocation MR_FIRE_1 = JojoMod.resLoc("block/mr_fire_1");
	private static final List<Flame> ANKH_FLAMES = new ArrayList<>();

	static {
		addFlame(0.0F, -1.0F, 0.0F, 3, 10, Direction.UP);
		addFlame(0.0F, -14.5F, 0.0F, 3, 8, Direction.WEST);
		addFlame(0.0F, -14.5F, 0.0F, 3, 8, Direction.EAST);
		addFlame(4.0F, -16.0F, 0.0F, 2, 8, Direction.UP);
		addFlame(-5.0F, -17.0F, 0.0F, 2, 8, Direction.WEST);
		addFlame(-4.0F, -26.0F, 0.0F, 2, 8, Direction.DOWN);
		addFlame(5.0F, -25.0F, 0.0F, 2, 8, Direction.EAST);
	}

	public MRCrossfireHurricaneRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(MRCrossfireHurricaneEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}

	@Override
	public void render(MRCrossfireHurricaneEntity entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("mr_crossfire_render_gate", entity, "MR Crossfire renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			poseStack.pushPose();
			poseStack.scale(1.0F, -1.0F, -1.0F);
			float xRotation = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
			poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
			poseStack.mulPose(Axis.XP.rotationDegrees(xRotation));
			float scale = entity.getScale() * (entity.isSpecial() ? 0.5F : 1.0F);
			VisualPipelineDiagnostics.logOnce("mr_crossfire_render_" + entity.getType().builtInRegistryHolder().key().location(),
					"MR Crossfire renderer reached: entityId={}, type={}, special={}, scale={}, pos={}.",
					entity.getId(), entity.getType().builtInRegistryHolder().key().location(), entity.isSpecial(), scale, entity.position());
			poseStack.scale(scale, scale, scale);
			renderAnkh(poseStack, buffer.getBuffer(Sheets.cutoutBlockSheet()));
			poseStack.popPose();
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	private void renderAnkh(PoseStack poseStack, VertexConsumer vertexBuilder) {
		TextureAtlasSprite fire0 = fireSprite(MR_FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(MR_FIRE_1);
		for (Flame flame : ANKH_FLAMES) {
			renderFlame(poseStack, vertexBuilder, flame, fire0, fire1);
		}
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void addFlame(float x, float y, float z, float width, float height, Direction flameDirection) {
		ANKH_FLAMES.add(new Flame(x, y, z, width, height, flameDirection));
	}

	private static void renderFlame(PoseStack poseStack, VertexConsumer vertexBuilder, Flame flame, TextureAtlasSprite fire0, TextureAtlasSprite fire1) {
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
				vertex(poseStack, vertexBuilder, xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU1, texV1);
				vertex(poseStack, vertexBuilder, -xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU0, texV1);
				vertex(poseStack, vertexBuilder, -xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU0, texV0);
				vertex(poseStack, vertexBuilder, xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU1, texV0);
				heightLeft -= 0.45F;
				yOffset -= 0.45F;
				xHalfWidth *= 0.9F;
				zOffset += 0.03F;
			}
			poseStack.popPose();
		}
		poseStack.popPose();
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

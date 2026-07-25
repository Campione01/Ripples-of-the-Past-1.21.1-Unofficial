package com.github.standobyte.jojoimpl.stands.silverchariot.client;

import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityModel;
import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityRenderer;
import com.github.standobyte.jojo.client.entityrender.stand.StandOpacityPolicy;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCRapierEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

public class SCRapierRenderer extends SimpleEntityRenderer<SCRapierEntity, SimpleEntityModel<SCRapierEntity>> {
	private static final ResourceLocation FIRE_0 = ResourceLocation.withDefaultNamespace("block/fire_0");
	private static final ResourceLocation FIRE_1 = ResourceLocation.withDefaultNamespace("block/fire_1");
	private static final Flame[] PROJECTILE_RAPIER_FLAMES = {
			new Flame(0.0F, -1.0F, 3.0F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 4.5F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 6.0F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 7.5F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 9.0F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 10.5F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 12.0F, 1, 3, Direction.NORTH),
			new Flame(0.0F, -1.0F, 13.5F, 1, 3, Direction.NORTH)
	};

	public SCRapierRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(JojoMod.resLoc("textures/entity/silver_chariot.png"), true);
		initResourceModel(JojoMod.resLoc("sc_rapier"), SimpleEntityModel::new, true);
		offsetModelByEntityHeight(false);
	}

	@Override
	protected void doRender(SCRapierEntity entity, SimpleEntityModel<SCRapierEntity> model,
			float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		StandEntity stand = entity.getOwner() instanceof StandEntity ownerStand ? ownerStand : null;
		float alpha = StandOpacityPolicy.apply(1.0F, stand != null ? stand.getUser() : null);
		RenderType renderType = alpha > 0.0F
				? ModRenderTypes.standTranslucentCull(getTextureLocation(entity))
				: buffer instanceof OutlineBufferSource ? RenderType.outline(getTextureLocation(entity)) : null;
		if (renderType != null) {
			int alphaChannel = Mth.clamp((int) (alpha * 255.0F), 0, 255);
			model.renderToBuffer(poseStack, buffer.getBuffer(renderType), packedLight,
					OverlayTexture.NO_OVERLAY, (alphaChannel << 24) | 0x00FFFFFF);
		}
		if (!entity.isOnFire() || alpha <= 0.0F) {
			return;
		}
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		renderRapierFlames(poseStack,
				buffer.getBuffer(ModRenderTypes.standTranslucent(InventoryMenu.BLOCK_ATLAS)), alpha);
		poseStack.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(SCRapierEntity entity) {
		return super.getTextureLocation(entity);
	}

	private static void renderRapierFlames(PoseStack poseStack, VertexConsumer vertexBuilder, float alpha) {
		TextureAtlasSprite fire0 = fireSprite(FIRE_0);
		TextureAtlasSprite fire1 = fireSprite(FIRE_1);
		for (Flame flame : PROJECTILE_RAPIER_FLAMES) {
			renderFlame(poseStack, vertexBuilder, flame, fire0, fire1, alpha);
		}
	}

	private static TextureAtlasSprite fireSprite(ResourceLocation sprite) {
		return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite);
	}

	private static void renderFlame(PoseStack poseStack, VertexConsumer vertexBuilder, Flame flame,
			TextureAtlasSprite fire0, TextureAtlasSprite fire1, float alpha) {
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
				vertex(poseStack, vertexBuilder, alpha, xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU1, texV1);
				vertex(poseStack, vertexBuilder, alpha, -xHalfWidth - xOffset, xOffset - yOffset, zOffset, texU0, texV1);
				vertex(poseStack, vertexBuilder, alpha, -xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU0, texV0);
				vertex(poseStack, vertexBuilder, alpha, xHalfWidth - xOffset, 1.4F - yOffset, zOffset, texU1, texV0);
				heightLeft -= 0.45F;
				yOffset -= 0.45F;
				xHalfWidth *= 0.9F;
				zOffset += 0.03F;
			}
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void vertex(PoseStack poseStack, VertexConsumer vertexBuilder, float alpha,
			float x, float y, float z, float u, float v) {
		PoseStack.Pose pose = poseStack.last();
		vertexBuilder.addVertex(pose.pose(), x, y, z)
				.setColor(255, 255, 255, Mth.clamp((int) (alpha * 255.0F), 0, 255))
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

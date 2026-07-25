package com.github.standobyte.jojoimpl.powers.hamon.client;

import java.util.List;

import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonZoomPunchEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class HamonZoomPunchRenderer extends EntityRenderer<HamonZoomPunchEntity> {
	private static final float MODEL_LENGTH = 12.0F / 16.0F;
	private static final float MODEL_OFFSET = 2.0F / 16.0F;
	private final ZoomPunchModel model;

	public HamonZoomPunchRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new ZoomPunchModel(createZoomPunchLayer().bakeRoot());
	}

	@Override
	public ResourceLocation getTextureLocation(HamonZoomPunchEntity entity) {
		Entity owner = entity.getOwner();
		if (owner instanceof AbstractClientPlayer player) {
			return player.getSkin().texture();
		}
		if (owner != null) {
			return getRendererTexture(owner);
		}
		AbstractClientPlayer player = Minecraft.getInstance().player;
		return player != null ? player.getSkin().texture() : DefaultPlayerSkin.get(entity.getUUID()).texture();
	}

	@Override
	public boolean shouldRender(HamonZoomPunchEntity entity, Frustum frustum, double camX, double camY, double camZ) {
		if (entity.getOwner() == Minecraft.getInstance().player) {
			return true;
		}
		return super.shouldRender(entity, frustum, camX, camY, camZ);
	}

	@Override
	public void render(HamonZoomPunchEntity entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			renderArm(entity, partialTick, poseStack, buffer,
					getOwnerPackedLight(entity, partialTick, packedLight));
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	private int getOwnerPackedLight(HamonZoomPunchEntity entity, float partialTick, int fallbackLight) {
		LivingEntity owner = entity.getOwner();
		return owner != null ? entityRenderDispatcher.getPackedLightCoords(owner, partialTick) : fallbackLight;
	}

	private void renderArm(HamonZoomPunchEntity entity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		Vec3 entityPos = entity.getPosition(partialTick);
		Vec3 originPos = entity.getOriginPoint(partialTick);
		Vec3 stretchVec = entityPos.subtract(originPos);
		float length = (float) stretchVec.length();
		if (length <= 1.0E-5F) {
			return;
		}

		Entity owner = entity.getOwner();
		boolean playerOwner = owner instanceof AbstractClientPlayer;
		boolean slim = owner instanceof AbstractClientPlayer player
				&& player.getSkin().model() == PlayerSkin.Model.SLIM;
		model.setVisibility(entity.getSide() == HumanoidArm.LEFT, playerOwner, slim);

		float yRot = MathUtil.yRotDegFromVec(stretchVec);
		float xRot = MathUtil.xRotDegFromVec(stretchVec);
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
		poseStack.mulPose(Axis.XP.rotationDegrees(xRot));
		poseStack.translate(0.0F, 0.0F, -MODEL_OFFSET);
		poseStack.scale(1.0F, 1.0F, (length + 2.0F * MODEL_OFFSET) / MODEL_LENGTH);
		poseStack.translate(0.0F, 0.0F, MODEL_OFFSET);
		poseStack.mulPose(Axis.XP.rotationDegrees(-xRot));
		poseStack.mulPose(Axis.YP.rotationDegrees(-yRot));

		model.setupAnim(yRot, xRot);
		VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
		model.renderToBuffer(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		poseStack.popPose();
	}

	private static LayerDefinition createZoomPunchLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartPose armPose = PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F, -Mth.HALF_PI, 0.0F, 0.0F);
		root.addOrReplaceChild("mobPunchRight", armBox(40, 16, -2.0F, 4.0F, 0.0F), armPose);
		root.addOrReplaceChild("mobPunchLeft", armBox(40, 16, -2.0F, 4.0F, 0.0F).mirror(), armPose);
		root.addOrReplaceChild("playerPunchRight", armBox(40, 16, -2.0F, 4.0F, 0.0F), armPose);
		root.addOrReplaceChild("playerPunchLeft", armBox(32, 48, -2.0F, 4.0F, 0.0F), armPose);
		root.addOrReplaceChild("playerPunchRightSlim", armBox(40, 16, -2.0F, 3.0F, 0.0F), armPose);
		root.addOrReplaceChild("playerPunchLeftSlim", armBox(32, 48, -2.0F, 3.0F, 0.0F), armPose);
		root.addOrReplaceChild("playerSleeveRight", armBox(40, 32, -2.0F, 4.0F, 0.25F), armPose);
		root.addOrReplaceChild("playerSleeveLeft", armBox(48, 48, -2.0F, 4.0F, 0.25F), armPose);
		root.addOrReplaceChild("playerSleeveRightSlim", armBox(40, 32, -2.0F, 3.0F, 0.25F), armPose);
		root.addOrReplaceChild("playerSleeveLeftSlim", armBox(48, 48, -2.0F, 3.0F, 0.25F), armPose);
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static CubeListBuilder armBox(int texU, int texV, float x, float width, float inflate) {
		return CubeListBuilder.create()
				.texOffs(texU, texV)
				.addBox(x, -10.0F, -2.0F, width, 12.0F, 4.0F, new CubeDeformation(inflate));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private ResourceLocation getRendererTexture(Entity owner) {
		EntityRenderer renderer = entityRenderDispatcher.getRenderer(owner);
		return renderer.getTextureLocation(owner);
	}

	private static final class ZoomPunchModel {
		private final ModelPart root;
		private final ModelPart mobPunchRight;
		private final ModelPart mobPunchLeft;
		private final ModelPart playerPunchRight;
		private final ModelPart playerPunchLeft;
		private final ModelPart playerPunchRightSlim;
		private final ModelPart playerPunchLeftSlim;
		private final ModelPart playerSleeveRight;
		private final ModelPart playerSleeveLeft;
		private final ModelPart playerSleeveRightSlim;
		private final ModelPart playerSleeveLeftSlim;
		private final List<ModelPart> armVariants;

		private ZoomPunchModel(ModelPart root) {
			this.root = root;
			this.mobPunchRight = root.getChild("mobPunchRight");
			this.mobPunchLeft = root.getChild("mobPunchLeft");
			this.playerPunchRight = root.getChild("playerPunchRight");
			this.playerPunchLeft = root.getChild("playerPunchLeft");
			this.playerPunchRightSlim = root.getChild("playerPunchRightSlim");
			this.playerPunchLeftSlim = root.getChild("playerPunchLeftSlim");
			this.playerSleeveRight = root.getChild("playerSleeveRight");
			this.playerSleeveLeft = root.getChild("playerSleeveLeft");
			this.playerSleeveRightSlim = root.getChild("playerSleeveRightSlim");
			this.playerSleeveLeftSlim = root.getChild("playerSleeveLeftSlim");
			this.armVariants = List.of(mobPunchRight, mobPunchLeft,
					playerPunchRight, playerPunchLeft, playerPunchRightSlim, playerPunchLeftSlim,
					playerSleeveRight, playerSleeveLeft, playerSleeveRightSlim, playerSleeveLeftSlim);
			setVisibility(false, false, false);
		}

		private void setVisibility(boolean leftSide, boolean player, boolean slim) {
			armVariants.forEach(part -> part.visible = false);
			if (!player) {
				(leftSide ? mobPunchLeft : mobPunchRight).visible = true;
			}
			else if (leftSide) {
				(slim ? playerPunchLeftSlim : playerPunchLeft).visible = true;
				(slim ? playerSleeveLeftSlim : playerSleeveLeft).visible = true;
			}
			else {
				(slim ? playerPunchRightSlim : playerPunchRight).visible = true;
				(slim ? playerSleeveRightSlim : playerSleeveRight).visible = true;
			}
		}

		private void setupAnim(float yRotDeg, float xRotDeg) {
			float yRot = yRotDeg * Mth.DEG_TO_RAD;
			float xRot = -Mth.HALF_PI + xRotDeg * Mth.DEG_TO_RAD;
			for (ModelPart part : armVariants) {
				if (part.visible) {
					part.yRot = yRot;
					part.xRot = xRot;
				}
			}
		}

		private void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
				int packedLight, int packedOverlay, int color) {
			root.render(poseStack, vertexConsumer, packedLight, packedOverlay, color);
		}
	}
}

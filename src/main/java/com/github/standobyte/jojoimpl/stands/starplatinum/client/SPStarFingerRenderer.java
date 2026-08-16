package com.github.standobyte.jojoimpl.stands.starplatinum.client;

import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityRenderer;
import com.github.standobyte.jojo.client.entityrender.stand.StandOpacityPolicy;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
import com.github.standobyte.jojoimpl.stands.starplatinum.SPStarFingerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class SPStarFingerRenderer extends EntityRenderer<SPStarFingerEntity> {
	private static final ResourceLocation BASE_TEXTURE = JojoMod.resLoc("textures/entity/projectiles/sp_star_finger.png");
	private static final float MAIN_PART_LENGTH = 2F / 16F;
	private static final float REPEATING_PART_LENGTH = 8F / 16F;
	private final ModelPart finger;
	private final ModelPart fingerExtending;

	public SPStarFingerRenderer(EntityRendererProvider.Context context) {
		super(context);
		ModelPart root = createStarFingerLayer().bakeRoot();
		this.finger = root.getChild("finger");
		this.fingerExtending = root.getChild("finger_extending");
	}

	private static LayerDefinition createStarFingerLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("finger", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 1.0F, 2.0F),
				PartPose.ZERO);
		root.addOrReplaceChild("finger_extending", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-1.0F, -1.0F, 1.0F, 2.0F, 1.0F, 8.0F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public ResourceLocation getTextureLocation(SPStarFingerEntity entity) {
		StandSkin standSkin = SimpleEntityRenderer.getStandSkin(entity);
		return standSkin != null ? standSkin.getTexture(BASE_TEXTURE) : BASE_TEXTURE;
	}

	@Override
	public void render(SPStarFingerEntity entity, float yRotation, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		Minecraft minecraft = Minecraft.getInstance();
		StandEntity ownerStand = entity.getOwner() instanceof StandEntity stand ? stand : null;
		if (!minecraft.options.getCameraType().isFirstPerson()
				|| ownerStand == null || ownerStand.getUser() != minecraft.getCameraEntity()) {
			return;
		}
		if (!entity.isInvisible() || !entity.isInvisibleTo(minecraft.player)) {
			renderFinger(entity, partialTick, poseStack, buffer,
					getOwnerPackedLight(entity, partialTick, packedLight), getAlpha(entity, partialTick));
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	private int getOwnerPackedLight(SPStarFingerEntity entity, float partialTick, int fallbackLight) {
		LivingEntity owner = entity.getOwner();
		return owner != null ? entityRenderDispatcher.getPackedLightCoords(owner, partialTick) : fallbackLight;
	}

	private float getAlpha(SPStarFingerEntity entity, float partialTick) {
		StandEntity stand = entity.getOwner() instanceof StandEntity ownerStand ? ownerStand : null;
		float lifecycleAlpha = 1.0F;
		if (entity.standDamage() && stand != null) {
			float modelAlpha = stand.modelAlpha.lerp(partialTick);
			float rangeAlpha = (float) stand.rangeEfficiency * modelAlpha;
			lifecycleAlpha = Mth.clamp(Math.max(rangeAlpha, modelAlpha * 0.15F), 0.0F, 1.0F);
		}
		return StandOpacityPolicy.apply(lifecycleAlpha, stand != null ? stand.getUser() : null);
	}

	private void renderFinger(SPStarFingerEntity entity, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, float alpha) {
		Vec3 entityPos = entity.getPosition(partialTick);
		Vec3 originPos = entity.getOriginPoint(partialTick);
		Vec3 extentVec = entityPos.subtract(originPos);
		float length = (float) extentVec.length();
		if (length <= 1.0E-5F) {
			return;
		}

		poseStack.pushPose();
		// Original ExtendingEntityRenderer anchors the repeating model at the projectile endpoint.
		// The Z flip below makes the cuboids extend back toward the owner/origin.
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extentVec)));
		poseStack.mulPose(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extentVec)));
		RenderType renderType = alpha > 0.0F
				? ModRenderTypes.standTranslucentCull(getTextureLocation(entity))
				: buffer instanceof OutlineBufferSource ? RenderType.outline(getTextureLocation(entity)) : null;
		if (renderType != null) {
			renderRepeatingFinger(length, finger, fingerExtending, poseStack,
					buffer.getBuffer(renderType), packedLight, alpha);
		}
		poseStack.popPose();
	}

	private static void renderRepeatingFinger(float length, ModelPart finger, ModelPart fingerExtending,
			PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, float alpha) {
		float modelLength = length;
		if (modelLength >= MAIN_PART_LENGTH) {
			renderSegment(finger, poseStack, vertexBuilder, packedLight, alpha);
			modelLength -= MAIN_PART_LENGTH;
		}
		while (modelLength >= REPEATING_PART_LENGTH) {
			renderSegment(fingerExtending, poseStack, vertexBuilder, packedLight, alpha);
			modelLength -= REPEATING_PART_LENGTH;
			poseStack.translate(0, 0, REPEATING_PART_LENGTH);
		}
		renderSegment(fingerExtending, poseStack, vertexBuilder, packedLight, alpha);
	}

	private static void renderSegment(ModelPart segment, PoseStack poseStack, VertexConsumer vertexBuilder,
			int packedLight, float alpha) {
		int alphaChannel = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		segment.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, (alphaChannel << 24) | 0x00FFFFFF);
	}
}

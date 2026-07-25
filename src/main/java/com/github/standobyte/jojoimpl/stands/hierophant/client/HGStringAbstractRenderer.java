package com.github.standobyte.jojoimpl.stands.hierophant.client;

import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityRenderer;
import com.github.standobyte.jojo.client.entityrender.stand.StandOpacityPolicy;
import com.github.standobyte.jojo.client.rendertype.ModRenderTypes;
import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.VisualPipelineDiagnostics;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.util.functions.MathUtil;
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

public abstract class HGStringAbstractRenderer<T extends OwnerBoundProjectileEntity> extends EntityRenderer<T> {
	private static final ResourceLocation BASE_TEXTURE = JojoMod.resLoc("textures/entity/projectiles/hg_string.png");
	private static final ResourceLocation GLOW_TEXTURE = JojoMod.resLoc("textures/entity/projectiles/hg_string_glow.png");
	private static final float REPEATING_PART_LENGTH = 8F / 16F;
	private final ModelPart stringSegment;

	protected HGStringAbstractRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.stringSegment = createStringLayer().bakeRoot().getChild("barrier");
	}

	private static LayerDefinition createStringLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("barrier", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 8.0F),
				PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return texture(entity, BASE_TEXTURE);
	}

	@Override
	public void render(T entity, float yRotation, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		VisualPipelineDiagnostics.logEntityVisibilityOnce("hg_string_render_gate", entity, "HG string renderer gate reached");
		if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
			int ownerLight = getOwnerPackedLight(entity, partialTick, packedLight);
			float alpha = getAlpha(entity, partialTick);
			Vec3 extentVec = entity.getPosition(partialTick).subtract(entity.getOriginPoint(partialTick));
			VisualPipelineDiagnostics.logOnce("hg_string_render_" + entity.getType().builtInRegistryHolder().key().location(),
					"HG string renderer reached: entityId={}, type={}, length={}, alpha={}, texture={}, glowTexture={}, pos={}.",
					entity.getId(), entity.getType().builtInRegistryHolder().key().location(), extentVec.length(), alpha,
					getTextureLocation(entity), texture(entity, GLOW_TEXTURE), entity.position());
			if (alpha > 0.0F) {
				renderString(entity, partialTick, poseStack, buffer, getTextureLocation(entity), ownerLight, alpha, false);
				renderString(entity, partialTick, poseStack, buffer, texture(entity, GLOW_TEXTURE), ClientUtil.MAX_LIGHT, alpha, false);
			}
			else if (buffer instanceof OutlineBufferSource) {
				renderString(entity, partialTick, poseStack, buffer, getTextureLocation(entity), ownerLight, alpha, true);
			}
			super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
		}
	}

	private int getOwnerPackedLight(T entity, float partialTick, int fallbackLight) {
		LivingEntity owner = entity.getOwner();
		return owner != null ? entityRenderDispatcher.getPackedLightCoords(owner, partialTick) : fallbackLight;
	}

	private float getAlpha(T entity, float partialTick) {
		StandEntity stand = entity.getOwner() instanceof StandEntity ownerStand ? ownerStand : null;
		float lifecycleAlpha = 1.0F;
		if (entity.standDamage() && stand != null) {
			float modelAlpha = stand.modelAlpha.lerp(partialTick);
			float rangeAlpha = (float) stand.rangeEfficiency * modelAlpha;
			lifecycleAlpha = Mth.clamp(Math.max(rangeAlpha, modelAlpha * 0.15F), 0.0F, 1.0F);
		}
		return StandOpacityPolicy.apply(lifecycleAlpha, stand != null ? stand.getUser() : null);
	}

	private ResourceLocation texture(T entity, ResourceLocation texture) {
		StandSkin standSkin = SimpleEntityRenderer.getStandSkin(entity);
		return standSkin != null ? standSkin.getTexture(texture) : texture;
	}

	private void renderString(T entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
			ResourceLocation texture, int packedLight, float alpha, boolean outlineOnly) {
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
		RenderType renderType = outlineOnly ? RenderType.outline(texture) : ModRenderTypes.standTranslucent(texture);
		renderRepeatingString(length, stringSegment, poseStack, buffer.getBuffer(renderType), packedLight, alpha);
		poseStack.popPose();
	}

	private static void renderRepeatingString(float length, ModelPart stringSegment, PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, float alpha) {
		float modelLength = length;
		while (modelLength >= REPEATING_PART_LENGTH) {
			renderStringSegment(stringSegment, poseStack, vertexBuilder, packedLight, alpha);
			modelLength -= REPEATING_PART_LENGTH;
			poseStack.translate(0, 0, REPEATING_PART_LENGTH);
		}
		renderStringSegment(stringSegment, poseStack, vertexBuilder, packedLight, alpha);
	}

	private static void renderStringSegment(ModelPart stringSegment, PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, float alpha) {
		int alphaChannel = Mth.clamp((int) (alpha * 255.0F), 0, 255);
		stringSegment.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, (alphaChannel << 24) | 0x00FFFFFF);
	}
}

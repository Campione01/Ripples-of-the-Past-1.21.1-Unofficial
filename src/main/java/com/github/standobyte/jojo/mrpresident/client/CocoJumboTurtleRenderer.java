package com.github.standobyte.jojo.mrpresident.client;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

public class CocoJumboTurtleRenderer extends LivingEntityRenderer<CocoJumboTurtleEntity, CocoJumboTurtleModel> {
	private static final ResourceLocation TURTLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png");
	private static final ResourceLocation TURTLE_EXTRA_LOCATION = JojoMod.resLoc("textures/entity/mob/turtle_extra.png");
	private static final ResourceLocation KEY_LOCATION = JojoMod.resLoc("textures/entity/mob/turtle_key.png");

	public CocoJumboTurtleRenderer(EntityRendererProvider.Context context) {
		super(context, new CocoJumboTurtleModel(context.bakeLayer(ModEntityTypeRenderers.COCO_JUMBO_TURTLE)), 0.7F);
		addLayer(new CocoJumboExtraTextureLayer(this));
		addLayer(new MrPresidentKeyLayer(this));
	}

	@Override
	public void render(CocoJumboTurtleEntity entity, float yRot, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		LivingEntity carrier = entity.getCarrier();
		if (carrier != null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (carrier == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson()) {
				Vec3 realOffset = CocoJumboTurtleEntity.carryOffset(
						Mth.lerp(partialTick, carrier.yBodyRotO, carrier.yBodyRot), carrier);
				Vec3 headRotOffset = CocoJumboTurtleEntity.carryOffset(
						Mth.lerp(partialTick, carrier.yRotO, carrier.getYRot()), carrier);
				Vec3 offset = headRotOffset.subtract(realOffset);
				poseStack.translate(offset.x, carrier.getBbHeight() * 0.2D, offset.z);
			}
			poseStack.scale(0.5F, 0.5F, 0.5F);
			poseStack.mulPose(Axis.YP.rotationDegrees(carrier.getMainArm() == HumanoidArm.RIGHT ? -60.0F : 60.0F));
		}
		super.render(entity, yRot, partialTick, poseStack, buffer, packedLight);
		poseStack.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(CocoJumboTurtleEntity entity) {
		return TURTLE_LOCATION;
	}

	@Override
	protected float getShadowRadius(CocoJumboTurtleEntity entity) {
		return entity.isCarried() ? 0.0F : super.getShadowRadius(entity);
	}

	private static class CocoJumboExtraTextureLayer extends RenderLayer<CocoJumboTurtleEntity, CocoJumboTurtleModel> {
		private CocoJumboExtraTextureLayer(RenderLayerParent<CocoJumboTurtleEntity, CocoJumboTurtleModel> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, CocoJumboTurtleEntity entity,
				float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TURTLE_EXTRA_LOCATION));
			getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);
		}
	}

	private static class MrPresidentKeyLayer extends RenderLayer<CocoJumboTurtleEntity, CocoJumboTurtleModel> {
		private MrPresidentKeyLayer(RenderLayerParent<CocoJumboTurtleEntity, CocoJumboTurtleModel> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, CocoJumboTurtleEntity entity,
				float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (entity.hasKey()) {
				VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(KEY_LOCATION));
				getParentModel().renderKeyToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, -1);
			}
		}
	}
}

package com.github.standobyte.jojoimpl.powers.pillarman.client;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.entityaction.ActionPhase;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojoimpl.powers.pillarman.abilities.PillarmanWindCloakAbility;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public class WindCloakLayer<T extends LivingEntity, M extends HumanoidModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/wind_cloak.png");
	private static final int COLOR = 0x40333333;
	private final HumanoidModel<T> layerModel;

	public WindCloakLayer(RenderLayerParent<T, M> renderer, HumanoidModel<T> layerModel) {
		super(renderer);
		this.layerModel = layerModel;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (!isWindCloakActive(livingEntity)) {
			return;
		}

		float tick = livingEntity.tickCount + partialTick;
		layerModel.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, partialTick);
		getParentModel().copyPropertiesTo(layerModel);
		layerModel.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.energySwirl(TEXTURE, xOffset(tick), tick * 0.01F));
		layerModel.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, COLOR);
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
			LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		if (entityRenderer.getModel() instanceof HumanoidModel<?> model && isWindCloakActive(entity)) {
			float tick = entity.tickCount + partialTick;
			FirstPersonModelLayer.setupForFirstPersonRender(model, entity, partialTick);
			VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.energySwirl(TEXTURE, xOffset(tick), tick * 0.01F));
			FirstPersonModelLayer.renderArmAndOuter(model, side, poseStack, vertexBuilder, light,
					OverlayTexture.NO_OVERLAY, COLOR, FirstPersonModelLayer.isRipplesAnimPlaying(model));
		}
	}

	private static boolean isWindCloakActive(LivingEntity entity) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(entity);
		return action != null && action.ability instanceof PillarmanWindCloakAbility
				&& action.getPhase() == ActionPhase.PERFORM;
	}

	private static float xOffset(float tick) {
		return tick * 0.01F;
	}
}

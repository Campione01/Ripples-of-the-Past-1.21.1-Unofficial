package com.github.standobyte.jojoimpl.powers.hamon.client;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
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

public class HamonProtectionLayer<T extends LivingEntity, M extends HumanoidModel<T>>
		extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/biped/hamon_protection.png");
	private static final int THIRD_PERSON_COLOR = 0x1A262626;
	private static final int FIRST_PERSON_COLOR = 0x40262626;
	private final HumanoidModel<T> layerModel;

	public HamonProtectionLayer(RenderLayerParent<T, M> renderer, HumanoidModel<T> layerModel) {
		super(renderer);
		this.layerModel = layerModel;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (livingEntity.isInvisible() || getTexture(livingEntity) == null) {
			return;
		}

		float tick = livingEntity.tickCount + partialTick;
		layerModel.prepareMobModel(livingEntity, limbSwing, limbSwingAmount, partialTick);
		getParentModel().copyPropertiesTo(layerModel);
		layerModel.setupAnim(livingEntity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.energySwirl(TEXTURE, xOffset(tick), tick * 0.002F));
		layerModel.renderToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT, OverlayTexture.NO_OVERLAY, THIRD_PERSON_COLOR);
	}

	@Nullable
	private static ResourceLocation getTexture(LivingEntity entity) {
		PlayerPower playerPower = PlayerPower.get(entity);
		return playerPower != null && playerPower.getCurTypeData(HamonPowerType.HAMON)
				.filter(HamonData::isProtectionEnabled)
				.isPresent() ? TEXTURE : null;
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
			LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		if (entityRenderer.getModel() instanceof HumanoidModel<?> model && getTexture(entity) != null) {
			float tick = entity.tickCount + partialTick;
			FirstPersonModelLayer.setupForFirstPersonRender(model, entity, partialTick);
			VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.energySwirl(TEXTURE, xOffset(tick), tick * 0.01F));
			FirstPersonModelLayer.renderArmAndOuter(model, side, poseStack, vertexBuilder, ClientUtil.MAX_LIGHT,
					OverlayTexture.NO_OVERLAY, FIRST_PERSON_COLOR, FirstPersonModelLayer.isRipplesAnimPlaying(model));
		}
	}

	private static float xOffset(float tick) {
		return tick * 0.01F;
	}
}

package com.github.standobyte.jojoimpl.powers.pillarman.client;

import com.github.standobyte.jojo.client.firstperson.FirstPersonModelLayer;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanData;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanPowerType;
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

public class PillarmanBladesLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> implements FirstPersonModelLayer {
	private static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/layer/pillarman_blades.png");
	private final PillarmanBladesModel<T> bladesModel;

	public PillarmanBladesLayer(RenderLayerParent<T, M> renderer, PillarmanBladesModel<T> bladesModel) {
		super(renderer);
		this.bladesModel = bladesModel;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		if (!getBladesVisible(livingEntity)) {
			return;
		}

		poseStack.pushPose();
		if (getParentModel().young) {
			poseStack.translate(0.0D, 0.75D, 0.0D);
			poseStack.scale(0.5F, 0.5F, 0.5F);
		}
		getParentModel().copyPropertiesTo(bladesModel);
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
		bladesModel.renderToBuffer(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}

	private static boolean getBladesVisible(LivingEntity entity) {
		PlayerPower playerPower = PlayerPower.get(entity);
		if (playerPower == null) {
			return false;
		}
		return playerPower.getCurTypeData(PillarmanPowerType.PILLAR_MAN)
				.filter(PillarmanData::getBladesVisible)
				.isPresent();
	}

	@Override
	public void renderHandFirstPerson(HumanoidArm side, PoseStack poseStack, MultiBufferSource buffer, int light,
			LivingEntity entity, LivingEntityRenderer<?, ?> entityRenderer, float partialTick) {
		// Original 1.16.5 layer also left first-person blade rendering as a FIXME.
	}
}

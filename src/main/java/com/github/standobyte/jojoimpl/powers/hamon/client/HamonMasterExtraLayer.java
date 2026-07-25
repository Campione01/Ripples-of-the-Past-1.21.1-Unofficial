package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonMasterEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class HamonMasterExtraLayer extends RenderLayer<HamonMasterEntity, HamonMasterModel> {
	private final HamonMasterModel model;
	private final ResourceLocation texture;

	public HamonMasterExtraLayer(RenderLayerParent<HamonMasterEntity, HamonMasterModel> renderer,
			HamonMasterModel model, ResourceLocation texture) {
		super(renderer);
		this.model = model;
		this.texture = texture;
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			HamonMasterEntity entity, float limbSwing, float limbSwingAmount, float partialTick,
			float ageInTicks, float netHeadYaw, float headPitch) {
		model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
		getParentModel().copyPropertiesTo(model);
		model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));
		model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY);
	}
}

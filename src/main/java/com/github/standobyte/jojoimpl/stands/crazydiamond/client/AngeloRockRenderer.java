package com.github.standobyte.jojoimpl.stands.crazydiamond.client;

import com.github.standobyte.jojo.client.entityrender.entities.SimpleEntityRenderer;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojoimpl.stands.crazydiamond.AngeloRockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class AngeloRockRenderer extends SimpleEntityRenderer<AngeloRockEntity, AngeloRockModel> {
	public static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/block/stone.png");
	public static final ResourceLocation SHADOW_TEXTURE = JojoMod.resLoc("textures/entity/angelo_rock_shadow.png");

	public AngeloRockRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.35F;
		initModel(new AngeloRockModel());
		initTexture(TEXTURE, false);
	}

	@Override
	protected void doRender(AngeloRockEntity entity, AngeloRockModel model, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		model.shadow.visible = false;
		BlockState blockUpper = entity.getUpperBlock();
		BlockState blockLower = entity.getLowerBlock();
		if (blockUpper.equals(blockLower)) {
			model.upperHalf.visible = true;
			model.lowerHalf.visible = true;
			renderRockPart(entity, blockUpper, model, partialTick, poseStack, buffer, packedLight);
		}
		else {
			model.upperHalf.visible = true;
			model.lowerHalf.visible = false;
			renderRockPart(entity, blockUpper, model, partialTick, poseStack, buffer, packedLight);
			model.upperHalf.visible = false;
			model.lowerHalf.visible = true;
			renderRockPart(entity, blockLower, model, partialTick, poseStack, buffer, packedLight);
		}

		if (entity.getCreationAnimProgress(partialTick) >= 1) {
			model.shadow.visible = true;
			model.upperHalf.visible = false;
			model.lowerHalf.visible = false;
			VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityTranslucent(SHADOW_TEXTURE));
			model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, BlitFloat.NO_TINT);
		}
	}

	private void renderRockPart(AngeloRockEntity entity, BlockState blockState, AngeloRockModel model,
			float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		ResourceLocation texture = CrazyDBlockBulletRenderer.getBlockTexture(blockState);
		if (texture == null) {
			texture = TEXTURE;
		}
		VertexConsumer vertexBuilder = buffer.getBuffer(model.renderType(texture));
		model.setCreationAnim(entity, entity.getCreationAnimProgress(partialTick));
		model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, BlitFloat.NO_TINT);
	}
}

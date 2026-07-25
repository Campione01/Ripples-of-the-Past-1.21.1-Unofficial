package com.github.standobyte.jojoimpl.powers.pillarman.client;

import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanDivineSandstormEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public class PillarmanDivineSandstormRenderer extends EntityRenderer<PillarmanDivineSandstormEntity> {

	public PillarmanDivineSandstormRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(PillarmanDivineSandstormEntity entity, float entityYaw, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(PillarmanDivineSandstormEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}

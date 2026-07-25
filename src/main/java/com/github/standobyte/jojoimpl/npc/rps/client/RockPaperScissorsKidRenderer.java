package com.github.standobyte.jojoimpl.npc.rps.client;

import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsKidEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RockPaperScissorsKidRenderer extends MobRenderer<RockPaperScissorsKidEntity, VillagerModel<RockPaperScissorsKidEntity>> {
	private static final ResourceLocation VILLAGER_BASE_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

	public RockPaperScissorsKidRenderer(EntityRendererProvider.Context context) {
		super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
	}

	@Override
	public ResourceLocation getTextureLocation(RockPaperScissorsKidEntity entity) {
		return VILLAGER_BASE_SKIN;
	}

	@Override
	protected void scale(RockPaperScissorsKidEntity entity, PoseStack poseStack, float partialTick) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}
}

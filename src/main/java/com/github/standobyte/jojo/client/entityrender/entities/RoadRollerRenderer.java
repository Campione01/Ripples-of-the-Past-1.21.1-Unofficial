package com.github.standobyte.jojo.client.entityrender.entities;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.ui.utils.BlitFloat;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.RoadRollerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RoadRollerRenderer extends SimpleEntityRenderer<RoadRollerEntity, RoadRollerModel> {
	public static final ResourceLocation TEXTURE = JojoMod.resLoc("textures/entity/road_roller.png");

	public RoadRollerRenderer(EntityRendererProvider.Context context) {
		super(context);
		initTexture(TEXTURE, false);
		initModel(new RoadRollerModel(context.bakeLayer(ModEntityTypeRenderers.ROAD_ROLLER)));
	}

	@Override
	protected void renderModel(RoadRollerEntity entity, RoadRollerModel model, float partialTick,
			PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight) {
		int overlay = entity.getTicksBeforeExplosion() > 0 && entity.getTicksBeforeExplosion() / 5 % 2 == 0
				? OverlayTexture.pack(OverlayTexture.u(1.0F), OverlayTexture.v(false))
				: OverlayTexture.NO_OVERLAY;
		model.renderToBuffer(poseStack, vertexBuilder, packedLight, overlay, BlitFloat.NO_TINT);
	}
}

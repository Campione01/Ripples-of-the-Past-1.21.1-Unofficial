package com.github.standobyte.jojo.client.itemrender;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.entityrender.entities.RoadRollerModel;
import com.github.standobyte.jojo.client.entityrender.entities.RoadRollerRenderer;
import com.github.standobyte.jojo.init.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RoadRollerItemRenderer extends BlockEntityWithoutLevelRenderer {
	@Nullable private RoadRollerModel roadRollerModel;

	public RoadRollerItemRenderer(Minecraft mc) {
		super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
	}

	@Override
	public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource buffer, int light, int overlay) {
		if (!itemStack.is(ModItems.ROAD_ROLLER.get())) {
			return;
		}
		RoadRollerModel model = getRoadRollerModel();
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		VertexConsumer vertex = ItemRenderer.getFoilBufferDirect(
				buffer, RenderType.entityCutoutNoCull(RoadRollerRenderer.TEXTURE), false, itemStack.hasFoil());
		model.renderToBuffer(poseStack, vertex, light, overlay, 0xFFFFFFFF);
		poseStack.popPose();
	}

	private RoadRollerModel getRoadRollerModel() {
		if (roadRollerModel == null) {
			roadRollerModel = new RoadRollerModel(Minecraft.getInstance().getEntityModels().bakeLayer(ModEntityTypeRenderers.ROAD_ROLLER));
		}
		return roadRollerModel;
	}
}

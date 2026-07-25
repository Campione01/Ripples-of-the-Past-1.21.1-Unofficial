package com.github.standobyte.jojo.client.itemrender;

import com.github.standobyte.jojo.client.itemrender.custommodel.CustomItemRenderer;
import com.github.standobyte.jojo.client.polaroid.PolaroidHelper;
import com.github.standobyte.jojo.core.JojoMod;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class PolaroidItemRenderer extends CustomItemRenderer {
	public PolaroidItemRenderer(Minecraft mc) {
		super(mc, JojoMod.resLoc("polaroid"), JojoMod.resLoc("textures/item/polaroid.png"));
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource buffer, int light, int overlay) {
		if (entity == Minecraft.getInstance().player && PolaroidHelper.isTakingPhoto()) {
			return;
		}
		super.renderByItem(stack, displayContext, poseStack, buffer, light, overlay);
	}
}

package com.github.standobyte.jojo.client.itemrender;

import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.client.itemrender.custommodel.CustomItemRenderer;
import com.github.standobyte.jojo.client.util.functions.ClientUtil;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.TommyGunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class TommyGunItemRenderer extends CustomItemRenderer {
	private static final ResourceLocation FIRE_1 = JojoMod.resLoc("textures/item/tommy_gun_fire_1.png");
	private static final ResourceLocation FIRE_2 = JojoMod.resLoc("textures/item/tommy_gun_fire_2.png");

	public TommyGunItemRenderer(Minecraft mc) {
		super(mc, JojoMod.resLoc("tommy_gun"), JojoMod.resLoc("textures/item/tommy_gun.png"));
	}

	@Override
	public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource buffer, int light, int overlay) {
		if (displayContext == ItemDisplayContext.GUI
				|| displayContext == ItemDisplayContext.GROUND
				|| displayContext == ItemDisplayContext.FIXED) {
			ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
			BakedModel model = itemRenderer.getModel(itemStack, null, entity, 0);
			CustomItemRenderer.renderItemNormally(poseStack, itemStack, displayContext, buffer, light, overlay, model);
			return;
		}
		super.renderByItem(itemStack, displayContext, poseStack, buffer, light, overlay);
	}

	@Override
	protected void doRender(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
			MultiBufferSource renderTypeBuffer, int light, int overlay) {
		ModelPart fire = firePart();
		boolean fireVisible = fire != null && fire.visible;
		if (fire != null) {
			fire.visible = false;
		}
		super.doRender(itemStack, displayContext, poseStack, renderTypeBuffer, light, overlay);
		if (fire != null) {
			fire.visible = fireVisible;
			renderFire(itemStack, poseStack, renderTypeBuffer, overlay, fire);
		}
	}

	private void renderFire(ItemStack itemStack, PoseStack poseStack, MultiBufferSource buffer, int overlay, ModelPart fire) {
		float fireTicks = TommyGunItem.getGunshotTick(itemStack) - Minecraft.getInstance().getTimer().getGameTimeDeltaTicks();
		if (fireTicks > 1.0F) {
			ResourceLocation texture = fireTicks >= 1.5F ? FIRE_2 : FIRE_1;
			VertexConsumer vertexBuilder = ItemRenderer.getFoilBufferDirect(
					buffer, renderType(texture), false, itemStack.hasFoil());
			fire.render(poseStack, vertexBuilder, ClientUtil.MAX_LIGHT, overlay, 0xFFFFFFFF);
		}
	}

	@Nullable
	private ModelPart firePart() {
		if (model.modelRoot == null) {
			return null;
		}
		try {
			return model.modelRoot.getChild("fire");
		}
		catch (NoSuchElementException e) {
			return null;
		}
	}
}

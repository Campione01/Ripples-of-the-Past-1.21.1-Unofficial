package rotp.core.client.ui.utils;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * An ability icon drawn from an item, tinted with the ARGB colour the HUD passes to every icon
 * (the unusable dimming and the disabled-controls fade), which GuiGraphics.renderItem ignores.
 */
public final class GuiItemIcon {
	private GuiItemIcon() {}

	/** @param entity the item's holder for its model overrides, or null for a fake item */
	public static void render(GuiGraphics guiGraphics, @Nullable LivingEntity entity, ItemStack stack, int x, int y, int color) {
		if (stack.isEmpty()) {
			return;
		}
		if (color == BlitFloat.NO_TINT) {
			if (entity != null) {
				guiGraphics.renderItem(entity, stack, x, y, 0);
			}
			else {
				guiGraphics.renderFakeItem(stack, x, y);
			}
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		ItemRenderer itemRenderer = mc.getItemRenderer();
		BakedModel model = itemRenderer.getModel(stack, mc.level, entity, 0);
		float[] rgba = shaderColor(color);
		PoseStack pose = guiGraphics.pose();
		guiGraphics.flush();
		guiGraphics.setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
		pose.pushPose();
		try {
			// the same placement as GuiGraphics.renderItem
			pose.translate(x + 8, y + 8, 150);
			pose.scale(16.0F, -16.0F, 16.0F);
			boolean flatLight = !model.usesBlockLight();
			if (flatLight) {
				Lighting.setupForFlatItems();
			}
			MultiBufferSource buffers = guiGraphics.bufferSource();
			if (needsTranslucentPass(color)) {
				// solid and cutout block items ignore the alpha unless they are drawn as translucent
				MultiBufferSource opaqueBuffers = buffers;
				buffers = renderType -> opaqueBuffers.getBuffer(translucentBlockType(renderType));
			}
			itemRenderer.render(stack, ItemDisplayContext.GUI, false, pose, buffers,
					LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
			guiGraphics.flush();
			if (flatLight) {
				Lighting.setupFor3DItems();
			}
		}
		finally {
			pose.popPose();
			guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		}
	}

	public static float[] shaderColor(int argb) {
		return new float[] {
				(argb >> 16 & 0xFF) / 255.0F,
				(argb >> 8 & 0xFF) / 255.0F,
				(argb & 0xFF) / 255.0F,
				(argb >>> 24) / 255.0F };
	}

	public static boolean needsTranslucentPass(int argb) {
		return (argb >>> 24) < 0xFF;
	}

	private static RenderType translucentBlockType(RenderType renderType) {
		return renderType == Sheets.solidBlockSheet() || renderType == Sheets.cutoutBlockSheet()
				? Sheets.translucentCullBlockSheet() : renderType;
	}
}

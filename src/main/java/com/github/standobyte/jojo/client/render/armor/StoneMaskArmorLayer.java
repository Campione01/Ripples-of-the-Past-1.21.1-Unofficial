package com.github.standobyte.jojo.client.render.armor;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.render.armor.model.StoneMaskArmorModel;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Legacy opt-in layer retained for binary compatibility.
 *
 * <p>The core renderer no longer installs this layer. Stone masks are owned
 * by the vanilla armor pass through {@link StoneMaskArmorClientExtensions},
 * which prevents the vanilla leather model and this layer from rendering the
 * same stack independently.</p>
 */
@Deprecated(forRemoval = false)
public class StoneMaskArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
	private StoneMaskArmorModel<T> model;

	public StoneMaskArmorLayer(RenderLayerParent<T, M> renderer) {
		super(renderer);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T livingEntity,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
			float netHeadYaw, float headPitch) {
		ItemStack stack = livingEntity.getItemBySlot(EquipmentSlot.HEAD);
		if (!(stack.getItem() instanceof StoneMaskItem)) {
			return;
		}

		StoneMaskArmorModel<T> maskModel = model();
		getParentModel().copyPropertiesTo(maskModel);
		maskModel.head.copyFrom(getParentModel().head);
		VertexConsumer vertexBuilder = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture(stack)));
		maskModel.renderToBuffer(poseStack, vertexBuilder, packedLight,
				LivingEntityRenderer.getOverlayCoords(livingEntity, 0.0F), 0xFFFFFFFF);
	}

	private StoneMaskArmorModel<T> model() {
		if (model == null) {
			model = new StoneMaskArmorModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModEntityTypeRenderers.STONE_MASK_ARMOR));
		}
		return model;
	}

	private static ResourceLocation texture(ItemStack stack) {
		return ((StoneMaskItem) stack.getItem())
				.getArmorTexture(stack);
	}
}

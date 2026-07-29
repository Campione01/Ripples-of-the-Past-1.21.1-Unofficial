package com.github.standobyte.jojo.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ItemMaterialTintPolicies;
import com.github.standobyte.jojo.client.render.item.InventoryItemHighlight;
import com.github.standobyte.jojoimpl.powers.hamon.client.particle.custom.FirstPersonHamonAura;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
	@ModifyVariable(
			method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At("HEAD"),
			argsOnly = true,
			ordinal = 1)
	private int jojo_ripples$highlightInventoryItem(int combinedOverlay, ItemStack itemStack, ItemDisplayContext displayContext,
			boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlayArg,
			BakedModel model) {
		if (!itemStack.isEmpty()) {
			float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			float overlayAmount = InventoryItemHighlight.getHighlightAmount(itemStack.getItem(), partialTick);
			if (overlayAmount >= 0) {
				return OverlayTexture.pack(overlayAmount, false);
			}
		}
		return combinedOverlay;
	}

	@Inject(
			method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At("HEAD"))
	private void jojo_ripples$renderFirstPersonHamonAura(ItemStack itemStack, ItemDisplayContext displayContext,
			boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay,
			BakedModel model, CallbackInfo ci) {
		if (itemStack.isEmpty()) {
			return;
		}
		switch (displayContext) {
		case FIRST_PERSON_LEFT_HAND:
			jojo_ripples$renderFirstPersonHamonAura(poseStack, bufferSource, itemStack, HumanoidArm.LEFT);
			break;
		case FIRST_PERSON_RIGHT_HAND:
			jojo_ripples$renderFirstPersonHamonAura(poseStack, bufferSource, itemStack, HumanoidArm.RIGHT);
			break;
		default:
			break;
		}
	}

	private static void jojo_ripples$renderFirstPersonHamonAura(PoseStack poseStack, MultiBufferSource bufferSource,
			ItemStack itemStack, HumanoidArm handSide) {
		poseStack.pushPose();
		FirstPersonHamonAura.itemMatrixTransform(poseStack, handSide, itemStack);
		FirstPersonHamonAura.getInstance().renderParticles(poseStack, bufferSource, handSide);
		poseStack.popPose();
	}

	@ModifyArg(
			method = "render(Lnet/minecraft/world/item/ItemStack;"
					+ "Lnet/minecraft/world/item/ItemDisplayContext;"
					+ "ZLcom/mojang/blaze3d/vertex/PoseStack;"
					+ "Lnet/minecraft/client/renderer/MultiBufferSource;"
					+ "IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/"
							+ "ItemRenderer;renderModelLists("
							+ "Lnet/minecraft/client/resources/model/BakedModel;"
							+ "Lnet/minecraft/world/item/ItemStack;"
							+ "IILcom/mojang/blaze3d/vertex/PoseStack;"
							+ "Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
			index = 5)
	private VertexConsumer jojo_ripples$itemMaterialTint(
			VertexConsumer original,
			@Local(argsOnly = true) ItemStack itemStack,
			@Local(argsOnly = true)
			ItemDisplayContext displayContext) {
		return ItemMaterialTintPolicies.wrap(
				original, itemStack, displayContext);
	}
}

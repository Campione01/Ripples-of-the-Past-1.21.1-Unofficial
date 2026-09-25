package rotp.core.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.client.render.ItemMaterialTintPolicies;
import rotp.core.client.render.item.InventoryItemHighlight;
import rotp.core.impl.powers.hamon.client.particle.custom.FirstPersonHamonAura;
import rotp.core.impl.stands.goldexperience.client.GEImbuedGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

	// GE-marked items swap the enchantment foil for the imbued-with-life glint (1.16 ItemRendererMixin).
	@WrapOperation(
			method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getFoilBufferDirect(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private VertexConsumer jojo_ripples$geGlintDirect(MultiBufferSource bufferSource, RenderType renderType,
			boolean noEntity, boolean withGlint, Operation<VertexConsumer> original,
			@Local(argsOnly = true) ItemStack itemStack, @Local(argsOnly = true) ItemDisplayContext displayContext,
			@Local(argsOnly = true) PoseStack poseStack) {
		if (GEImbuedGlint.isMarked(itemStack)) {
			return GEImbuedGlint.foilBufferDirect(bufferSource, renderType, itemStack, displayContext, poseStack);
		}
		return original.call(bufferSource, renderType, noEntity, withGlint);
	}

	@WrapOperation(
			method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;ZZ)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private VertexConsumer jojo_ripples$geGlint(MultiBufferSource bufferSource, RenderType renderType,
			boolean isItem, boolean glint, Operation<VertexConsumer> original,
			@Local(argsOnly = true) ItemStack itemStack) {
		if (GEImbuedGlint.isMarked(itemStack)) {
			return GEImbuedGlint.foilBuffer(bufferSource, renderType);
		}
		return original.call(bufferSource, renderType, isItem, glint);
	}

	@WrapOperation(
			method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;getCompassFoilBuffer(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
	private VertexConsumer jojo_ripples$geGlintCompass(MultiBufferSource bufferSource, RenderType renderType,
			PoseStack.Pose pose, Operation<VertexConsumer> original, @Local(argsOnly = true) ItemStack itemStack) {
		if (GEImbuedGlint.isMarked(itemStack)) {
			return GEImbuedGlint.compassFoilBuffer(bufferSource, renderType, pose);
		}
		return original.call(bufferSource, renderType, pose);
	}
}

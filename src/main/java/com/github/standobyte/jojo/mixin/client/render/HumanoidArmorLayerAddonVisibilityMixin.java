package com.github.standobyte.jojo.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.standobyte.jojo.api.client.render.ScopedHumanoidArmorVisibility;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerAddonVisibilityMixin {
	private static final String RENDER_ARMOR_PIECE =
			"renderArmorPiece("
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/MultiBufferSource;"
			+ "Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/world/entity/EquipmentSlot;"
			+ "I"
			+ "Lnet/minecraft/client/model/HumanoidModel;"
			+ "FFFFFF)V";

	@Inject(
			method = RENDER_ARMOR_PIECE,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/layers/"
							+ "HumanoidArmorLayer;setPartVisibility("
							+ "Lnet/minecraft/client/model/HumanoidModel;"
							+ "Lnet/minecraft/world/entity/EquipmentSlot;)V",
					shift = At.Shift.AFTER))
	private void jojo_ripples$applyAddonArmorVisibility(
			PoseStack poseStack,
			MultiBufferSource buffer,
			LivingEntity entity,
			EquipmentSlot slot,
			int packedLight,
			HumanoidModel<?> armorModel,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci) {
		ScopedHumanoidArmorVisibility.applyForArmorPiece(entity, armorModel);
	}

	@Inject(method = RENDER_ARMOR_PIECE, at = @At("RETURN"))
	private void jojo_ripples$restoreAddonArmorVisibility(
			PoseStack poseStack,
			MultiBufferSource buffer,
			LivingEntity entity,
			EquipmentSlot slot,
			int packedLight,
			HumanoidModel<?> armorModel,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci) {
		ScopedHumanoidArmorVisibility.restoreAfterArmorPiece(armorModel);
	}

	@WrapOperation(
			method = RENDER_ARMOR_PIECE,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;get("
							+ "Lnet/minecraft/core/component/DataComponentType;"
							+ ")Ljava/lang/Object;"))
	private Object jojo_ripples$skipStoneMaskArmorTrim(
			ItemStack stack,
			DataComponentType<?> componentType,
			Operation<Object> original) {
		return stack.getItem() instanceof StoneMaskItem
				? null
				: original.call(stack, componentType);
	}

	@WrapOperation(
			method = RENDER_ARMOR_PIECE,
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
	private boolean jojo_ripples$skipStoneMaskArmorGlint(
			ItemStack stack,
			Operation<Boolean> original) {
		return !(stack.getItem() instanceof StoneMaskItem)
				&& original.call(stack);
	}
}

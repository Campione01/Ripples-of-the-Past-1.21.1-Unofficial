package com.github.standobyte.jojo.client.render.armor;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.render.armor.model.StoneMaskArmorModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

public class StoneMaskArmorClientExtensions implements IClientItemExtensions {
	private StoneMaskArmorModel<LivingEntity> model;

	public static void register(
			RegisterClientExtensionsEvent event,
			Item... stoneMasks) {
		event.registerItem(
				new StoneMaskArmorClientExtensions(),
				stoneMasks);
	}

	public static boolean ownsArmorPass(Item item) {
		return IClientItemExtensions.of(item)
				instanceof StoneMaskArmorClientExtensions;
	}

	@Override
	public HumanoidModel<?> getHumanoidArmorModel(
			LivingEntity livingEntity,
			ItemStack itemStack,
			EquipmentSlot equipmentSlot,
			HumanoidModel<?> original) {
		if (equipmentSlot != EquipmentSlot.HEAD) {
			return original;
		}
		if (model == null) {
			model = new StoneMaskArmorModel<>(
					Minecraft.getInstance().getEntityModels()
							.bakeLayer(
									ModEntityTypeRenderers
											.STONE_MASK_ARMOR));
		}
		if (StoneMaskArmorRenderDiagnostics.isTracking(livingEntity)) {
			StoneMaskArmorRenderDiagnostics.record(
					livingEntity, itemStack, equipmentSlot, original, model);
		}
		return model;
	}
}

package com.github.standobyte.jojo.client.render.armor;

import com.github.standobyte.jojo.client.ModEntityTypeRenderers;
import com.github.standobyte.jojo.client.render.armor.model.SatiporojaScarfArmorModel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class SatiporojaScarfArmorClientExtensions implements IClientItemExtensions {
	private SatiporojaScarfArmorModel<LivingEntity> model;

	@Override
	public HumanoidModel<?> getHumanoidArmorModel(LivingEntity livingEntity, ItemStack itemStack,
			EquipmentSlot equipmentSlot, HumanoidModel<?> original) {
		if (equipmentSlot != EquipmentSlot.HEAD) {
			return original;
		}
		if (model == null) {
			model = new SatiporojaScarfArmorModel<>(Minecraft.getInstance().getEntityModels()
					.bakeLayer(ModEntityTypeRenderers.SATIPOROJA_SCARF_ARMOR));
		}
		return model;
	}
}

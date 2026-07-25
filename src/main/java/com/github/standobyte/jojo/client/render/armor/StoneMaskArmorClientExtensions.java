package com.github.standobyte.jojo.client.render.armor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class StoneMaskArmorClientExtensions implements IClientItemExtensions {
	@Override
	public int getArmorLayerTintColor(ItemStack stack, LivingEntity entity, ArmorMaterial.Layer layer, int layerIdx, int fallbackColor) {
		return 0;
	}
}

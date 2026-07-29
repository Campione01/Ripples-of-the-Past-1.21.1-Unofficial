package com.github.standobyte.jojo.api.client.render;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record ItemMaterialTintQuery(
		ItemStack stack,
		ItemDisplayContext displayContext) {}

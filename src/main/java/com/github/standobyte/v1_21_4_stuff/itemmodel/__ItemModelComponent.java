package com.github.standobyte.v1_21_4_stuff.itemmodel;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModItemDataComponents;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class __ItemModelComponent {

	public static UnaryOperator<DataComponentType.Builder<ResourceLocation>> builder() {
		return builder -> builder
				.persistent(ResourceLocation.CODEC)
				.networkSynchronized(ResourceLocation.STREAM_CODEC)
				.cacheEncoding();
	}

	public static void set(ItemStack item, ResourceLocation itemModel) {
		item.set(ModItemDataComponents.ITEM_MODEL.get()/*DataComponents.ITEM_MODEL*/, itemModel);
	}

	@Nullable
	public static ResourceLocation get(ItemStack item) {
		return item.get(ModItemDataComponents.ITEM_MODEL.get());
	}
}

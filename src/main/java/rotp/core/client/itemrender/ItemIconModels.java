package rotp.core.client.itemrender;

import java.util.HashMap;

import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class ItemIconModels {
	public static final ResourceLocation MOD_LOGO = null; // the default model of the debug item
	protected static HashMap<ResourceLocation, ItemStack> cache = new HashMap<>();
	
	public static ItemStack makeIconItem(ResourceLocation model) {
		return cache.computeIfAbsent(model, modelPath -> {
			ItemStack item = new ItemStack(ModItems.DEBUG_ITEM.get());
			if (modelPath != null) {
				item.set(ModItemDataComponents.ITEM_MODEL.get(), modelPath);
			}
			return item;
		});
	}
	
}

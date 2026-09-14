package rotp.core.api.client.render;

import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public record ItemMaterialTintQuery(
		ItemStack stack,
		ItemDisplayContext displayContext) {}

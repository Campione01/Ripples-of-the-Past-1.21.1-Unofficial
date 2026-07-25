package com.github.standobyte.v1_21_4_stuff.missingmethods;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

public class _ItemStack {

    public static Component getStyledHoverName(ItemStack item) {
        MutableComponent mutablecomponent = Component.empty().append(item.getHoverName()).withStyle(item.getRarity().getStyleModifier());
        if (item.has(DataComponents.CUSTOM_NAME)) {
            mutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        return mutablecomponent;
    }
    
    public static boolean isBroken(ItemStack item) {
    	return item.isDamageableItem() && item.getDamageValue() >= item.getMaxDamage();
    }
    
    public static boolean isOnCooldown(ItemStack item, ItemCooldowns cooldowns) {
    	return !item.isEmpty() && cooldowns.isOnCooldown(item.getItem());
    }
}

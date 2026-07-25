package com.github.standobyte.jojo.item;

import java.util.List;

import com.github.standobyte.jojo.init.ModArmorMaterials;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class BreathControlMaskItem extends ArmorItem {
	public BreathControlMaskItem(Properties properties) {
		super(ModArmorMaterials.BREATH_CONTROL_MASK, ArmorItem.Type.HELMET, properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.jojo_ripples.breath_control_mask.hint").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal(" ").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.jojo_ripples.breath_control_mask.hint2").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal(" "));
	}
}

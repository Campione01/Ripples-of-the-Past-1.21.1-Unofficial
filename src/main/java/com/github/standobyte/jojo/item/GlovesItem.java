package com.github.standobyte.jojo.item;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public class GlovesItem extends Item {
	private static final int ENCHANTMENT_VALUE = 15;
	private static final ItemAttributeModifiers GLOVES_ATTRIBUTES = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE,
					new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
			.build();

	public GlovesItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
		return GLOVES_ATTRIBUTES;
	}

	@Override
	public int getEnchantmentValue(ItemStack stack) {
		return ENCHANTMENT_VALUE;
	}

	@Override
	public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.supportsEnchantment(stack, enchantment) || enchantment.is(Enchantments.KNOCKBACK);
	}

	@Override
	public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.isPrimaryItemFor(stack, enchantment) || enchantment.is(Enchantments.KNOCKBACK);
	}

	public boolean openFingers() {
		return true;
	}
}

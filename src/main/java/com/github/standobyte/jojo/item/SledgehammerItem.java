package com.github.standobyte.jojo.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class SledgehammerItem extends PickaxeItem {
	public SledgehammerItem(Properties properties) {
		super(Tiers.IRON, properties.durability(Tiers.IRON.getUses()).attributes(DiggerItem.createAttributes(Tiers.IRON, 9.0F, -3.3F)));
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entityLiving) {
		if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
			stack.hurtAndBreak(2, entityLiving, EquipmentSlot.MAINHAND);
		}
		return true;
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		// Original sledgehammer spends 1 durability in hurtEnemy; suppress DiggerItem's default 2 point post-hit cost.
	}

	@Override
	public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.supportsEnchantment(stack, enchantment) || isOriginalWeaponEnchantment(enchantment);
	}

	@Override
	public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
		return super.isPrimaryItemFor(stack, enchantment) || isOriginalWeaponEnchantment(enchantment);
	}

	private static boolean isOriginalWeaponEnchantment(Holder<Enchantment> enchantment) {
		return !enchantment.is(Enchantments.SWEEPING_EDGE)
				&& (enchantment.is(Enchantments.SHARPNESS)
				|| enchantment.is(Enchantments.SMITE)
				|| enchantment.is(Enchantments.BANE_OF_ARTHROPODS)
				|| enchantment.is(Enchantments.KNOCKBACK)
				|| enchantment.is(Enchantments.FIRE_ASPECT)
				|| enchantment.is(Enchantments.LOOTING));
	}
}

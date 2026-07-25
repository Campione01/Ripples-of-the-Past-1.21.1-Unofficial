package com.github.standobyte.jojo.event;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.item.GlovesItem;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class GlovesAnvilEventHandler {
	private static final int TOO_EXPENSIVE_COST = 40;

	private GlovesAnvilEventHandler() {}

	@SubscribeEvent
	public static void combineGloves(AnvilUpdateEvent event) {
		ItemStack left = event.getLeft();
		ItemStack right = event.getRight();
		if (!(left.getItem() instanceof GlovesItem) || !left.is(right.getItem())) {
			return;
		}

		ItemStack output = left.copy();
		ItemEnchantments.Mutable mergedEnchantments = new ItemEnchantments.Mutable(
				EnchantmentHelper.getEnchantmentsForCrafting(left));
		int addedCost = 0;
		boolean acceptedEnchantment = false;
		boolean rejectedEnchantment = false;

		for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
			Holder<Enchantment> enchantment = entry.getKey();
			int leftLevel = mergedEnchantments.getLevel(enchantment);
			int rightLevel = entry.getIntValue();
			int mergedLevel = leftLevel == rightLevel ? rightLevel + 1 : Math.max(leftLevel, rightLevel);
			boolean canApply = event.getPlayer().getAbilities().instabuild || left.supportsEnchantment(enchantment);

			for (Holder<Enchantment> existing : mergedEnchantments.keySet()) {
				if (!existing.equals(enchantment) && !Enchantment.areCompatible(enchantment, existing)) {
					canApply = false;
					addedCost++;
				}
			}

			if (!canApply) {
				rejectedEnchantment = true;
				continue;
			}

			acceptedEnchantment = true;
			mergedLevel = Math.min(mergedLevel, enchantment.value().getMaxLevel());
			mergedEnchantments.set(enchantment, mergedLevel);
			addedCost += enchantment.value().getAnvilCost() * mergedLevel;
			if (left.getCount() > 1) {
				addedCost = TOO_EXPENSIVE_COST;
			}
		}

		if (rejectedEnchantment && !acceptedEnchantment) {
			return;
		}

		int renameCost = applyName(event.getName(), left, output);
		addedCost += renameCost;
		if (addedCost <= 0) {
			return;
		}

		long baseCost = (long) left.getOrDefault(DataComponents.REPAIR_COST, 0)
				+ right.getOrDefault(DataComponents.REPAIR_COST, 0);
		long totalCost = Math.min(baseCost + addedCost, Integer.MAX_VALUE);
		if (renameCost == addedCost && totalCost >= TOO_EXPENSIVE_COST) {
			totalCost = TOO_EXPENSIVE_COST - 1;
		}
		if (totalCost >= TOO_EXPENSIVE_COST && !event.getPlayer().getAbilities().instabuild) {
			return;
		}

		int repairCost = Math.max(output.getOrDefault(DataComponents.REPAIR_COST, 0),
				right.getOrDefault(DataComponents.REPAIR_COST, 0));
		if (renameCost != addedCost || renameCost == 0) {
			repairCost = AnvilMenu.calculateIncreasedRepairCost(repairCost);
		}
		output.set(DataComponents.REPAIR_COST, repairCost);
		EnchantmentHelper.setEnchantments(output, mergedEnchantments.toImmutable());
		event.setOutput(output);
		event.setCost(totalCost);
	}

	private static int applyName(String name, ItemStack left, ItemStack output) {
		if (name == null || StringUtil.isBlank(name)) {
			if (left.has(DataComponents.CUSTOM_NAME)) {
				output.remove(DataComponents.CUSTOM_NAME);
				return 1;
			}
		} else if (!name.equals(left.getHoverName().getString())) {
			output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
			return 1;
		}
		return 0;
	}
}

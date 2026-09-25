package rotp.core.mixin.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.init.ModEnchantments;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

/**
 * 1.16 Forge filtered table candidates with canApplyAtEnchantingTable(stack) || (isBook && isAllowedOnBooks()).
 * NeoForge treats a plain Book as primary for every enchantment, so drop #jojo_ripples:not_allowed_on_books here.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperBookFilterMixin {

	@Inject(method = "getAvailableEnchantmentResults", at = @At("RETURN"), cancellable = true)
	private static void rotp$dropBookDisallowed(int level, ItemStack stack, Stream<Holder<Enchantment>> possibleEnchantments,
			CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
		if (!stack.is(Items.BOOK)) {
			return;
		}
		List<EnchantmentInstance> results = cir.getReturnValue();
		if (results.stream().anyMatch(e -> e.enchantment.is(ModEnchantments.NOT_ALLOWED_ON_BOOKS))) {
			List<EnchantmentInstance> filtered = new ArrayList<>(results);
			filtered.removeIf(e -> e.enchantment.is(ModEnchantments.NOT_ALLOWED_ON_BOOKS));
			cir.setReturnValue(filtered);
		}
	}
}

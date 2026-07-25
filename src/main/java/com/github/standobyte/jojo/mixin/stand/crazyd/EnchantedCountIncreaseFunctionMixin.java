package com.github.standobyte.jojo.mixin.stand.crazyd;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.standobyte.jojoimpl.stands.crazydiamond.AngeloRockEntity;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

@Mixin(EnchantedCountIncreaseFunction.class)
public class EnchantedCountIncreaseFunctionMixin {

	@Redirect(method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/entity/LivingEntity;)I"))
	private int jojo_ripples$angeloRockFortuneAsLooting(Holder<Enchantment> enchantment, LivingEntity attacker, ItemStack stack, LootContext context) {
		int originalLooting = EnchantmentHelper.getEnchantmentLevel(enchantment, attacker);
		if (enchantment.is(Enchantments.LOOTING)) {
			if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity livingEntity) {
				int fortune = AngeloRockEntity.getMobLootFortuneLevel(livingEntity);
				return Math.max(originalLooting, fortune);
			}
		}
		return originalLooting;
	}
}

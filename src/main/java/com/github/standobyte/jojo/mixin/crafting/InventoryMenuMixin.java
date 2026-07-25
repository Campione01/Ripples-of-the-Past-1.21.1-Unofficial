package com.github.standobyte.jojo.mixin.crafting;

import com.github.standobyte.jojo.crafting.StandUserCraftingContext;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
	@Shadow @Final private Player owner;

	@Inject(method = "recipeMatches", at = @At("HEAD"))
	private void rotp$standRecipeBookStart(RecipeHolder<CraftingRecipe> recipe, CallbackInfoReturnable<Boolean> ci) {
		StandUserCraftingContext.push(owner);
	}

	@Inject(method = "recipeMatches", at = @At("RETURN"))
	private void rotp$standRecipeBookEnd(RecipeHolder<CraftingRecipe> recipe, CallbackInfoReturnable<Boolean> ci) {
		StandUserCraftingContext.pop();
	}
}

package com.github.standobyte.jojo.mixin.crafting;

import com.github.standobyte.jojo.crafting.StandUserCraftingContext;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin {
	@Shadow @Final private Player player;

	@Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"))
	private static void rotp$standRecipeStart(AbstractContainerMenu menu, Level level, Player player,
			CraftingContainer craftSlots, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> currentRecipe,
			CallbackInfo ci) {
		StandUserCraftingContext.push(player);
	}

	@Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
	private static void rotp$standRecipeEnd(AbstractContainerMenu menu, Level level, Player player,
			CraftingContainer craftSlots, ResultContainer resultSlots, RecipeHolder<CraftingRecipe> currentRecipe,
			CallbackInfo ci) {
		StandUserCraftingContext.pop();
	}

	@Inject(method = "recipeMatches", at = @At("HEAD"))
	private void rotp$standRecipeBookStart(RecipeHolder<CraftingRecipe> recipe, CallbackInfoReturnable<Boolean> ci) {
		StandUserCraftingContext.push(player);
	}

	@Inject(method = "recipeMatches", at = @At("RETURN"))
	private void rotp$standRecipeBookEnd(RecipeHolder<CraftingRecipe> recipe, CallbackInfoReturnable<Boolean> ci) {
		StandUserCraftingContext.pop();
	}
}

package com.github.standobyte.jojo.crafting;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModRecipeSerializers;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.cassette.CassetteData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class CassetteCopyRecipe extends CustomRecipe {
	public CassetteCopyRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		CopyInput copyInput = readInput(input);
		return copyInput != null && !copyInput.original.isEmpty() && copyInput.blankCount > 0
				&& !CassetteRecordedItem.getOrBroken(copyInput.original).isBroken()
				&& CassetteRecordedItem.getOrBroken(copyInput.original).generation() < CassetteData.MAX_GENERATION;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		CopyInput copyInput = readInput(input);
		if (copyInput == null || copyInput.original.isEmpty() || copyInput.blankCount <= 0) {
			return ItemStack.EMPTY;
		}
		CassetteData original = CassetteRecordedItem.getOrBroken(copyInput.original);
		if (original.isBroken() || original.generation() >= CassetteData.MAX_GENERATION) {
			return ItemStack.EMPTY;
		}
		ItemStack copies = new ItemStack(ModItems.CASSETTE_RECORDED.get(), copyInput.blankCount);
		copies.set(ModItemDataComponents.CASSETTE_DATA.get(), original.copyForNextGeneration());
		return copies;
	}

	private static CopyInput readInput(CraftingInput input) {
		ItemStack original = ItemStack.EMPTY;
		int blankCount = 0;
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModItems.CASSETTE_RECORDED.get())) {
				if (!original.isEmpty()) {
					return null;
				}
				original = stack;
			}
			else if (stack.is(ModItems.CASSETTE_BLANK.get())) {
				blankCount++;
			}
			else {
				return null;
			}
		}
		return new CopyInput(original, blankCount);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> items = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.getItem().hasCraftingRemainingItem()) {
				items.set(i, new ItemStack(stack.getItem().getCraftingRemainingItem()));
			}
			else if (stack.is(ModItems.CASSETTE_RECORDED.get())) {
				items.set(i, stack.copyWithCount(1));
				break;
			}
		}
		return items;
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width >= 3 && height >= 3;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.CASSETTE_COPY.get();
	}

	private record CopyInput(ItemStack original, int blankCount) {}
}

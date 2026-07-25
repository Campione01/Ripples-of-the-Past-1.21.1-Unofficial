package com.github.standobyte.jojo.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModRecipeSerializers;
import com.github.standobyte.jojo.item.cassette.CassetteData;
import com.github.standobyte.jojo.item.cassette.CassetteTrackSource;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class CassetteRecordingRecipe extends CustomRecipe {
	public CassetteRecordingRecipe(CraftingBookCategory category) {
		super(category);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		Recording recording = readInput(input);
		return recording != null && !recording.sources.isEmpty() && recording.blankCount > 0;
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		Recording recording = readInput(input);
		if (recording == null || recording.sources.isEmpty() || recording.blankCount <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = new ItemStack(ModItems.CASSETTE_RECORDED.get(), recording.blankCount);
		stack.set(com.github.standobyte.jojo.init.ModItemDataComponents.CASSETTE_DATA.get(),
				CassetteData.recorded(recording.sources, recording.ribbonColor));
		return stack;
	}

	private static Recording readInput(CraftingInput input) {
		int blankCount = 0;
		List<CassetteTrackSource> sources = new ArrayList<>();
		Optional<DyeColor> ribbonColor = null;

		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}

			Optional<CassetteTrackSource> source = CassetteTrackSource.fromItem(stack);
			if (source.isPresent()) {
				CassetteTrackSource track = source.get();
				sources.add(track);
				if (track.type() == CassetteTrackSource.Type.DYE_COLOR) {
					if (ribbonColor == null) {
						ribbonColor = track.dyeColor();
					}
					else {
						ribbonColor = Optional.empty();
					}
				}
			}
			else if (stack.is(ModItems.CASSETTE_BLANK.get())) {
				blankCount++;
			}
			else {
				return null;
			}
		}

		return new Recording(sources, blankCount, ribbonColor == null ? Optional.empty() : ribbonColor);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> items = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		for (int i = 0; i < input.size(); i++) {
			ItemStack stack = input.getItem(i);
			Optional<CassetteTrackSource> source = CassetteTrackSource.fromItem(stack);
			if (source.isPresent() && !source.get().sourceItemIsSpent()) {
				items.set(i, stack.copyWithCount(1));
			}
			else if (stack.getItem().hasCraftingRemainingItem()) {
				items.set(i, new ItemStack(stack.getItem().getCraftingRemainingItem()));
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
		return ModRecipeSerializers.CASSETTE_RECORD.get();
	}

	private record Recording(List<CassetteTrackSource> sources, int blankCount, Optional<DyeColor> ribbonColor) {}
}

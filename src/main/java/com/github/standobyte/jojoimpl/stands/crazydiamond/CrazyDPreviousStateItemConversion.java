package com.github.standobyte.jojoimpl.stands.crazydiamond;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;

import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

public final class CrazyDPreviousStateItemConversion {
	public static final Optional<Pair<ItemStack[], Integer>> EXISTS = Optional.of(Pair.of(new ItemStack[0], 0));

	private CrazyDPreviousStateItemConversion() {}

	public static Optional<Pair<ItemStack[], Integer>> convertTo(ItemStack item, Level level,
			@Nullable Predicate<Recipe<?>> additionalCondition, RandomSource random, boolean createItems) {
		if (item.isEmpty()) return Optional.empty();

		if (item.getItem() == Items.ENCHANTED_BOOK) {
			return createItems ? Optional.of(Pair.of(new ItemStack[]{new ItemStack(Items.BOOK)}, 1)) : EXISTS;
		}
		// TODO (1.16.5) free up the map id
		// if (item.getItem() == Items.FILLED_MAP) return createItems ? Optional.of(Pair.of(new ItemStack[]{new ItemStack(Items.MAP)}, 1)) : EXISTS;

		WrittenBookContent writtenBookContent = item.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (writtenBookContent != null) {
			if (createItems) {
				List<Filterable<String>> pages = new ArrayList<>(writtenBookContent.pages().size());
				for (Filterable<Component> page : writtenBookContent.pages()) {
					pages.add(page.map(Component::getString));
				}
				ItemStack writableBook = new ItemStack(Items.WRITABLE_BOOK);
				writableBook.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
				return Optional.of(Pair.of(new ItemStack[]{writableBook}, 1));
			}
			else {
				return EXISTS;
			}
		}

		HolderLookup.Provider trims = level.registryAccess();
		// TODO (1.16.5) revert brewing recipes
		return groupByPredicatesOrdered(
				level.getRecipeManager().getRecipes().stream().map(RecipeHolder::value), Util.make(new ArrayList<>(), list -> {
					// TODO (1.16.5) revert nbt recipes (including netherite armor)
					list.add(recipe -> recipe instanceof SmithingRecipe);
					list.add(recipe -> recipe instanceof AbstractCookingRecipe);
					list.add(recipe -> recipe instanceof StonecutterRecipe);
					list.add(recipe -> recipe instanceof CraftingRecipe);
					list.add(recipe -> true);
				}), recipe -> outputMatches(recipe, item, trims) && !bannedItem(item, level, trims) && (additionalCondition == null || additionalCondition.test(recipe)), false)
				.values().stream().filter(list -> !list.isEmpty()).findFirst()
				.flatMap(recipesOfPreferredType -> {
					Recipe<?> randomRecipe = recipesOfPreferredType.get(random.nextInt(recipesOfPreferredType.size()));
					ItemStack[] ingredients = getIngredients(randomRecipe, random);
					if (ingredients.length == 0) return Optional.empty();
					return Optional.of(Pair.of(ingredients, randomRecipe.getResultItem(trims).getCount()));
				});
	}

	public static <T> LinkedHashMap<Predicate<T>, List<T>> groupByPredicatesOrdered(Stream<T> elements, List<Predicate<T>> predicates,
			@Nullable Predicate<T> commonCondition, boolean elementRepeats) {
		LinkedHashMap<Predicate<T>, List<T>> map = Util.make(new LinkedHashMap<>(), m -> {
			predicates.forEach(key -> m.put(key, new ArrayList<>()));
		});
		elements.forEach(element -> {
			if (commonCondition == null || commonCondition.test(element)) {
				for (Predicate<T> predicate : predicates) {
					if (predicate.test(element)) {
						map.get(predicate).add(element);
						if (!elementRepeats) {
							break;
						}
					}
				}
			}
		});
		return map;
	}

	public static boolean outputMatches(Recipe<?> recipe, ItemStack stack, HolderLookup.Provider trims) {
		return recipe.getResultItem(trims).getItem() == stack.getItem() && recipe.getResultItem(trims).getCount() <= stack.getCount();
	}

	public static boolean bannedItem(ItemStack stack, Level level, HolderLookup.Provider trims) {
		return level.getRecipeManager().getRecipes().stream().map(RecipeHolder::value).anyMatch(recipe ->
		recipe.getResultItem(trims).getItem() == stack.getItem() && recipe instanceof BlastingRecipe);
	}

	public static ItemStack[] getIngredients(Recipe<?> recipe, RandomSource random) {
		List<Ingredient> ingredients = recipe.getIngredients();
		ItemStack[] stacks = new ItemStack[ingredients.size()];

		for (int i = 0; i < ingredients.size(); i++) {
			ItemStack[] matchingStacks = ingredients.get(i).getItems();
			stacks[i] = matchingStacks.length > 0 ? matchingStacks[random.nextInt(matchingStacks.length)].copy() : ItemStack.EMPTY;
		}

		return stacks;
	}
}

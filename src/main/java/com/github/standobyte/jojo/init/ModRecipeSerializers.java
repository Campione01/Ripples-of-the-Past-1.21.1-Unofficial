package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.crafting.CassetteCopyRecipe;
import com.github.standobyte.jojo.crafting.CassetteRecordingRecipe;
import com.github.standobyte.jojo.crafting.StandUserShapedRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
	public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
			DeferredRegister.create(Registries.RECIPE_SERIALIZER, JojoMod.MOD_ID);

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<StandUserShapedRecipe>> STAND_USER_SHAPED_RECIPE =
			RECIPE_SERIALIZERS.register("crafting_shaped_stand", StandUserShapedRecipe.Serializer::new);

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CassetteRecordingRecipe>> CASSETTE_RECORD =
			RECIPE_SERIALIZERS.register("cassette_record", () -> new SimpleCraftingRecipeSerializer<>(CassetteRecordingRecipe::new));

	public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CassetteCopyRecipe>> CASSETTE_COPY =
			RECIPE_SERIALIZERS.register("cassette_copy", () -> new SimpleCraftingRecipeSerializer<>(CassetteCopyRecipe::new));
}

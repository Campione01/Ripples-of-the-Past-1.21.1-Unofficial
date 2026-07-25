package com.github.standobyte.jojo.crafting;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModRecipeSerializers;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public class StandUserShapedRecipe implements CraftingRecipe {
	private final ShapedRecipe recipe;
	private final List<ResourceLocation> standIds;

	public StandUserShapedRecipe(ShapedRecipe recipe, List<ResourceLocation> standIds) {
		this.recipe = recipe;
		this.standIds = List.copyOf(standIds);
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return recipe.matches(input, level) && playerMatches(StandUserCraftingContext.getPlayer());
	}

	private boolean playerMatches(@Nullable Player player) {
		if (player == null) {
			return false;
		}
		StandPower standPower = StandPower.get(player);
		StandType standType = standPower != null && standPower.hasPower() ? standPower.getPowerType() : null;
		if (standType == null) {
			return false;
		}
		if (standIds.isEmpty()) {
			return true;
		}
		ResourceLocation standId = standType.getId();
		return standIds.stream().anyMatch(id -> standId.equals(id) || isLegacyModIdAlias(id, standId));
	}

	private static boolean isLegacyModIdAlias(ResourceLocation recipeId, ResourceLocation standId) {
		return recipeId.getNamespace().equals("jojo")
				&& standId.getNamespace().equals(JojoMod.MOD_ID)
				&& recipeId.getPath().equals(standId.getPath());
	}

	@Override
	public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
		return recipe.assemble(input, registries);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return recipe.canCraftInDimensions(width, height);
	}

	@Override
	public ItemStack getResultItem(HolderLookup.Provider registries) {
		return recipe.getResultItem(registries);
	}

	@Override
	public NonNullList<Ingredient> getIngredients() {
		return recipe.getIngredients();
	}

	@Override
	public boolean showNotification() {
		return recipe.showNotification();
	}

	@Override
	public String getGroup() {
		return recipe.getGroup();
	}

	@Override
	public boolean isIncomplete() {
		return recipe.isIncomplete();
	}

	@Override
	public CraftingBookCategory category() {
		return recipe.category();
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipeSerializers.STAND_USER_SHAPED_RECIPE.get();
	}

	public int getWidth() {
		return recipe.getWidth();
	}

	public int getHeight() {
		return recipe.getHeight();
	}

	private ShapedRecipe wrappedRecipe() {
		return recipe;
	}

	private List<ResourceLocation> standIds() {
		return standIds;
	}

	private record StandEntry(ResourceLocation name) {
		private static final Codec<StandEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("name").forGetter(StandEntry::name)
		).apply(instance, StandEntry::new));
	}

	public static class Serializer implements RecipeSerializer<StandUserShapedRecipe> {
		private static final MapCodec<List<ResourceLocation>> STANDS_CODEC = StandEntry.CODEC.listOf().fieldOf("stand")
				.xmap(entries -> entries.stream().map(StandEntry::name).toList(),
						ids -> ids.stream().map(StandEntry::new).toList());

		private static final MapCodec<StandUserShapedRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
				ShapedRecipe.Serializer.CODEC.forGetter(StandUserShapedRecipe::wrappedRecipe),
				STANDS_CODEC.forGetter(StandUserShapedRecipe::standIds)
		).apply(instance, StandUserShapedRecipe::new));

		private static final StreamCodec<RegistryFriendlyByteBuf, StandUserShapedRecipe> STREAM_CODEC =
				new StreamCodec<RegistryFriendlyByteBuf, StandUserShapedRecipe>() {
			@Override
			public StandUserShapedRecipe decode(RegistryFriendlyByteBuf buf) {
				ShapedRecipe shaped = ShapedRecipe.Serializer.STREAM_CODEC.decode(buf);
				int count = buf.readVarInt();
				List<ResourceLocation> stands = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					stands.add(buf.readResourceLocation());
				}
				return new StandUserShapedRecipe(shaped, stands);
			}

			@Override
			public void encode(RegistryFriendlyByteBuf buf, StandUserShapedRecipe recipe) {
				ShapedRecipe.Serializer.STREAM_CODEC.encode(buf, recipe.wrappedRecipe());
				buf.writeVarInt(recipe.standIds.size());
				for (ResourceLocation stand : recipe.standIds) {
					buf.writeResourceLocation(stand);
				}
			}
		};

		@Override
		public MapCodec<StandUserShapedRecipe> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, StandUserShapedRecipe> streamCodec() {
			return STREAM_CODEC;
		}
	}
}

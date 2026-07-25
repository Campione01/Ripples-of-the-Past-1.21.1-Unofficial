package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.loot.AdditionalSingleItemLootModifier;
import com.github.standobyte.jojo.loot.StrayFreezeArrowLootModifier;
import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModLootModifiers {
	public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
			DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, JojoMod.MOD_ID);
	
	public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<StrayFreezeArrowLootModifier>> STRAY_FREEZE_ARROW =
			LOOT_MODIFIER_SERIALIZERS.register("stray_freeze_arrow", () -> StrayFreezeArrowLootModifier.CODEC);

	public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<AdditionalSingleItemLootModifier>> ADDITIONAL_SINGLE_ITEM =
			LOOT_MODIFIER_SERIALIZERS.register("additional_single_item", () -> AdditionalSingleItemLootModifier.CODEC);
}

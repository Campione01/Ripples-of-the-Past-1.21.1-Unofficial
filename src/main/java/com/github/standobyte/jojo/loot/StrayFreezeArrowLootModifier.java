package com.github.standobyte.jojo.loot;

import com.github.standobyte.jojo.init.ModLootModifiers;
import com.github.standobyte.jojo.init.ModPotions;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class StrayFreezeArrowLootModifier extends LootModifier {
	public static final MapCodec<StrayFreezeArrowLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
			codecStart(instance).apply(instance, StrayFreezeArrowLootModifier::new));
	
	public StrayFreezeArrowLootModifier(LootItemCondition[] conditions) {
		super(conditions);
	}
	
	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		for (ItemStack stack : generatedLoot) {
			if (stack.is(Items.TIPPED_ARROW)) {
				PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
				if (potionContents != null && potionContents.is(Potions.SLOWNESS)) {
					stack.set(DataComponents.POTION_CONTENTS, potionContents.withPotion(ModPotions.FREEZE_POTION));
				}
			}
		}
		return generatedLoot;
	}
	
	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return ModLootModifiers.STRAY_FREEZE_ARROW.get();
	}
}

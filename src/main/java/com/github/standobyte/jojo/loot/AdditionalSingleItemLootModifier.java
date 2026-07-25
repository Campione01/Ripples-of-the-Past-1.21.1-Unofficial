package com.github.standobyte.jojo.loot;

import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.ModLootModifiers;
import com.github.standobyte.jojo.item.TommyGunItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class AdditionalSingleItemLootModifier extends LootModifier {
	public static final MapCodec<AdditionalSingleItemLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
			codecStart(instance)
			.and(BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(modifier -> modifier.item))
			.and(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("tommy_gun_ammo", 0).forGetter(modifier -> modifier.tommyGunAmmo))
			.and(Codec.BOOL.optionalFieldOf("replace", false).forGetter(modifier -> modifier.replace))
			.apply(instance, AdditionalSingleItemLootModifier::new));

	private final Item item;
	private final int tommyGunAmmo;
	private final boolean replace;

	public AdditionalSingleItemLootModifier(LootItemCondition[] conditions, Item item, int tommyGunAmmo, boolean replace) {
		super(conditions);
		this.item = item;
		this.tommyGunAmmo = tommyGunAmmo;
		this.replace = replace;
	}

	@Override
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		if (replace) {
			generatedLoot.clear();
		}
		ItemStack stack = new ItemStack(item);
		if (stack.is(ModItems.TOMMY_GUN.get()) && tommyGunAmmo > 0) {
			TommyGunItem.setAmmo(stack, tommyGunAmmo);
		}
		generatedLoot.add(stack);
		return generatedLoot;
	}

	@Override
	public MapCodec<? extends IGlobalLootModifier> codec() {
		return ModLootModifiers.ADDITIONAL_SINGLE_ITEM.get();
	}
}

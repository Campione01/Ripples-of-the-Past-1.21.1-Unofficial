package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.AddValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ModEnchantments {
	public static final ResourceKey<Enchantment> GLOVES_DAMAGE = key("gloves_damage");
	public static final ResourceKey<Enchantment> GLOVES_SPEED = key("gloves_speed");

	public static final TagKey<Item> GLOVES_ENCHANTABLE = TagKey.create(
			Registries.ITEM, JojoMod.resLoc("enchantable/gloves"));

	private ModEnchantments() {}

	public static void bootstrap(BootstrapContext<Enchantment> context) {
		HolderGetter<Item> items = context.lookup(Registries.ITEM);
		HolderSet<Item> gloves = items.getOrThrow(GLOVES_ENCHANTABLE);

		register(context, GLOVES_DAMAGE, Enchantment.enchantment(Enchantment.definition(
				gloves,
				gloves,
				10,
				5,
				Enchantment.dynamicCost(1, 11),
				Enchantment.dynamicCost(21, 11),
				1,
				EquipmentSlotGroup.MAINHAND))
				.withEffect(EnchantmentEffectComponents.DAMAGE,
						new AddValue(LevelBasedValue.perLevel(1.0F, 0.5F))));

		register(context, GLOVES_SPEED, Enchantment.enchantment(Enchantment.definition(
				gloves,
				gloves,
				2,
				4,
				Enchantment.dynamicCost(8, 14),
				Enchantment.dynamicCost(33, 14),
				4,
				EquipmentSlotGroup.HAND)));
	}

	private static void register(BootstrapContext<Enchantment> context, ResourceKey<Enchantment> key,
			Enchantment.Builder builder) {
		context.register(key, builder.build(key.location()));
	}

	private static ResourceKey<Enchantment> key(String path) {
		return ResourceKey.create(Registries.ENCHANTMENT, JojoMod.resLoc(path));
	}

	@SubscribeEvent
	public static void gatherData(GatherDataEvent event) {
		event.createDatapackRegistryObjects(new RegistrySetBuilder()
				.add(Registries.ENCHANTMENT, ModEnchantments::bootstrap));
	}
}

package com.github.standobyte.jojo.init;

import java.util.List;
import java.util.Map;

import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
	public static final int BLADE_HAT_DURABILITY_FACTOR = 12;
	public static final int BLADE_HAT_DURABILITY =
			ArmorItem.Type.HELMET.getDurability(BLADE_HAT_DURABILITY_FACTOR);
	public static final int BREATH_CONTROL_MASK_DURABILITY_FACTOR = 10;
	public static final int BREATH_CONTROL_MASK_DURABILITY =
			ArmorItem.Type.HELMET.getDurability(BREATH_CONTROL_MASK_DURABILITY_FACTOR);

	public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
			DeferredRegister.create(Registries.ARMOR_MATERIAL, JojoMod.MOD_ID);

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BLADE_HAT =
			ARMOR_MATERIALS.register("blade_hat", () -> new ArmorMaterial(
					Map.of(ArmorItem.Type.HELMET, 1),
					15,
					SoundEvents.ARMOR_EQUIP_GENERIC,
					() -> Ingredient.of(Items.BLACK_WOOL),
					List.of(new ArmorMaterial.Layer(JojoMod.resLoc("blade_hat"))),
					0.0F,
					0.0F));

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STONE_MASK =
			ARMOR_MATERIALS.register("stone_mask", () -> new ArmorMaterial(
					Map.of(ArmorItem.Type.HELMET, 1),
					15,
					SoundEvents.ARMOR_EQUIP_LEATHER,
					() -> Ingredient.of(Items.LEATHER),
					List.of(new ArmorMaterial.Layer(JojoMod.resLoc("stone_mask"))),
					0.0F,
					0.0F));

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BREATH_CONTROL_MASK =
			ARMOR_MATERIALS.register("breath_control_mask", () -> new ArmorMaterial(
					Map.of(ArmorItem.Type.HELMET, 1),
					9,
					SoundEvents.ARMOR_EQUIP_GENERIC,
					() -> Ingredient.of(Items.IRON_INGOT),
					List.of(new ArmorMaterial.Layer(JojoMod.resLoc("breath_control_mask"))),
					0.0F,
					0.0F));

	public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SATIPOROJA_SCARF =
			ARMOR_MATERIALS.register("satiporoja_scarf", () -> new ArmorMaterial(
					Map.of(ArmorItem.Type.HELMET, 1),
					25,
					SoundEvents.ARMOR_EQUIP_GENERIC,
					() -> Ingredient.EMPTY,
					List.of(new ArmorMaterial.Layer(JojoMod.resLoc("satiporoja_scarf"))),
					0.0F,
					0.0F));

	private ModArmorMaterials() {}
}

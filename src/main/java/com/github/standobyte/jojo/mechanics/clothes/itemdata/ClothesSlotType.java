package com.github.standobyte.jojo.mechanics.clothes.itemdata;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.mojang.serialization.Codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public enum ClothesSlotType implements StringRepresentable {
	HEAD("head"),
	CHEST("chest"),
	LEGS("legs"),
	FEET("feet");
	
	private final String name;
	
	private ClothesSlotType(String name) {
		this.name = name;
	}

	public static final Codec<ClothesSlotType> CODEC = StringRepresentable.fromEnum(ClothesSlotType::values);
	public static final StreamCodec<FriendlyByteBuf, ClothesSlotType> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(ClothesSlotType.class);
	
	@Override
	public String getSerializedName() {
		return name;
	}
	
	public static boolean canEquip(ItemStack item, ClothesSlotType slot) {
		ClothesDataComponent component = item.get(ModItemDataComponents.CLOTHES_PIECE);
		return component != null && component.getSlot() == slot;
	}
	
}
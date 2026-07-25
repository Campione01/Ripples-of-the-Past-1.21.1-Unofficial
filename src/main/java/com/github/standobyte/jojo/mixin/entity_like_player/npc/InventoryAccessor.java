package com.github.standobyte.jojo.mixin.entity_like_player.npc;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

@Mixin(Inventory.class)
public interface InventoryAccessor {
//	@Accessor("items") @Mutable public void setItems(NonNullList<ItemStack> items);
	@Accessor("armor") @Mutable public void setArmor(NonNullList<ItemStack> armor);
	@Accessor("offhand") @Mutable public void setOffhand(NonNullList<ItemStack> offhand);
	@Accessor("compartments") public List<NonNullList<ItemStack>> getCompartments();
	@Accessor("compartments") @Mutable public void setCompartments(List<NonNullList<ItemStack>> compartments);
}

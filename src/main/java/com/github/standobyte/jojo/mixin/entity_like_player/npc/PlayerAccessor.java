package com.github.standobyte.jojo.mixin.entity_like_player.npc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public interface PlayerAccessor {
	@Accessor("inventory") @Mutable public void setInventory(Inventory inventory);
	@Invoker("destroyVanishingCursedItems") public void invokeDestroyVanishingCursedItems();
}

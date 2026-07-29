package com.github.standobyte.jojo.api.trade;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public record ContextualVillagerTradeContext(
		ResourceLocation owner,
		ResourceLocation group,
		ServerPlayer player,
		Villager villager) {
	public ContextualVillagerTradeContext {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(group, "group");
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(villager, "villager");
	}
}

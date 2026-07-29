package com.github.standobyte.jojo.mechanics;

import com.github.standobyte.jojo.api.trade.ContextualVillagerTrades;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ContextualVillagerTradeEvents {
	private ContextualVillagerTradeEvents() {}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onVillagerInteract(
			PlayerInteractEvent.EntityInteract event) {
		if (!(event.getEntity()
				instanceof ServerPlayer player)
				|| !(event.getTarget()
						instanceof Villager villager)
				|| event.getItemStack().is(
						Items.VILLAGER_SPAWN_EGG)
				|| !villager.isAlive()
				|| villager.isTrading()
				|| villager.isSleeping()
				|| player.isSecondaryUseActive()
				|| villager.isBaby()
				|| villager.getOffers().isEmpty()) {
			return;
		}
		ContextualVillagerTrades.attemptFirstOffers(
				player, villager);
	}

	@SubscribeEvent
	public static void onTrade(
			TradeWithVillagerEvent event) {
		if (event.getEntity() instanceof ServerPlayer player
				&& event.getAbstractVillager()
						instanceof Villager villager) {
			ContextualVillagerTrades.onTrade(
					player,
					villager,
					event.getMerchantOffer());
		}
	}
}

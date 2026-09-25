package rotp.core.mechanics;

import java.util.UUID;

import rotp.core.api.trade.ContextualVillagerTrades;
import rotp.core.core.JojoMod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ContextualVillagerTradeEvents {
	// 1.16 MerchantData "RefuseTrade": players who scammed this villager
	private static final String REFUSE_TRADE =
			JojoMod.MOD_ID + ":RefuseTrade";

	private ContextualVillagerTradeEvents() {}

	public static void setRefusesTrading(
			Villager villager, UUID playerId, boolean refuse) {
		CompoundTag data = villager.getPersistentData();
		ListTag list = data.getList(REFUSE_TRADE, Tag.TAG_INT_ARRAY);
		IntArrayTag idTag = NbtUtils.createUUID(playerId);
		boolean present = list.contains(idTag);
		if (refuse && !present) {
			list.add(idTag);
		}
		else if (!refuse && present) {
			list.remove(idTag);
		}
		if (list.isEmpty()) {
			data.remove(REFUSE_TRADE);
		}
		else {
			data.put(REFUSE_TRADE, list);
		}
	}

	public static boolean refusesTradingWith(
			Villager villager, UUID playerId) {
		CompoundTag data = villager.getPersistentData();
		return data.contains(REFUSE_TRADE, Tag.TAG_LIST)
				&& data.getList(REFUSE_TRADE, Tag.TAG_INT_ARRAY)
						.contains(NbtUtils.createUUID(playerId));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onVillagerInteract(
			PlayerInteractEvent.EntityInteract event) {
		// 1.16 CustomVillagerTrades: a scammed villager refuses the thief
		if (event.getEntity() instanceof ServerPlayer thief
				&& event.getTarget() instanceof Villager scammed
				&& refusesTradingWith(scammed, thief.getUUID())) {
			scammed.setUnhappyCounter(40);
			scammed.playSound(SoundEvents.VILLAGER_NO,
					1.0F, scammed.getVoicePitch());
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.CONSUME);
			return;
		}
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

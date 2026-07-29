package com.github.standobyte.jojo.api.trade;

import javax.annotation.Nullable;

import net.minecraft.world.item.trading.MerchantOffer;

public interface ContextualVillagerTradeProvider {
	default boolean isEligible(
			ContextualVillagerTradeContext context) {
		return true;
	}

	@Nullable
	MerchantOffer createOffer(
			ContextualVillagerTradeContext context);

	default void onFirstPurchase(
			ContextualVillagerTradeContext context,
			MerchantOffer offer) {}
}

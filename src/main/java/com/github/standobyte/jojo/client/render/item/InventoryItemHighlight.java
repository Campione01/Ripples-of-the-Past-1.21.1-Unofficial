package com.github.standobyte.jojo.client.render.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

public final class InventoryItemHighlight {
	private static final Map<ResourceLocation, Integer> HIGHLIGHT_TIMER = new HashMap<>();
	private static final int CYCLE = 20;

	private InventoryItemHighlight() {
	}

	public static void highlightItem(Item item, int ticks) {
		if (item == null || ticks < CYCLE) {
			return;
		}
		ticks = ticks / CYCLE * CYCLE;
		ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
		Integer curTimer = HIGHLIGHT_TIMER.get(key);
		if (curTimer != null) {
			if (curTimer % CYCLE > 0) {
				ticks -= CYCLE;
			}
			HIGHLIGHT_TIMER.put(key, curTimer % CYCLE + ticks);
		}
		else {
			HIGHLIGHT_TIMER.put(key, ticks);
		}
	}

	public static void tick() {
		Iterator<Map.Entry<ResourceLocation, Integer>> iter = HIGHLIGHT_TIMER.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<ResourceLocation, Integer> entry = iter.next();
			int timer = entry.getValue() - 1;
			if (timer < 0) {
				iter.remove();
			}
			else {
				entry.setValue(timer);
			}
		}
	}

	public static float getHighlightAmount(Item item, float partialTick) {
		if (item == null) {
			return -1;
		}
		ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
		Integer ticks = HIGHLIGHT_TIMER.get(key);
		if (ticks != null && ticks >= 0) {
			float x = ticks + partialTick;
			x = x % CYCLE / (CYCLE / 2F);
			if (x > 1) {
				x = 2 - x;
			}
			return Mth.sqrt(x);
		}
		return -1;
	}
}

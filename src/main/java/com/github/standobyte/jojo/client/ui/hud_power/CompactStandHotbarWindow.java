package com.github.standobyte.jojo.client.ui.hud_power;

final class CompactStandHotbarWindow {
	private static final int MAX_VISIBLE_SLOTS = 5;

	static Range visibleRange(int slotCount, int selectedIndex, boolean compact) {
		int count = Math.max(slotCount, 0);
		if (!compact || count <= MAX_VISIBLE_SLOTS) {
			return new Range(0, count);
		}
		int selected = selectedIndex >= 0 && selectedIndex < count ? selectedIndex : 0;
		int start = Math.max(0, Math.min(selected - MAX_VISIBLE_SLOTS / 2,
				count - MAX_VISIBLE_SLOTS));
		return new Range(start, start + MAX_VISIBLE_SLOTS);
	}

	record Range(int startInclusive, int endExclusive) {}

	private CompactStandHotbarWindow() {}
}

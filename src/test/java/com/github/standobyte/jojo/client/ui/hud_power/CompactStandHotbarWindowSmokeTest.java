package com.github.standobyte.jojo.client.ui.hud_power;

public final class CompactStandHotbarWindowSmokeTest {
	public static void main(String[] args) {
		run();
		System.out.println("CompactStandHotbarWindowSmokeTest passed");
	}

	public static void run() {
		checkRange(0, -1, true, 0, 0);
		checkRange(1, 0, true, 0, 1);
		checkRange(5, 0, true, 0, 5);
		checkRange(5, 4, true, 0, 5);
		checkRange(6, 0, true, 0, 5);
		checkRange(6, 2, true, 0, 5);
		checkRange(6, 3, true, 1, 6);
		checkRange(6, 5, true, 1, 6);
		checkRange(20, 0, true, 0, 5);
		checkRange(20, 10, true, 8, 13);
		checkRange(20, 19, true, 15, 20);
		checkRange(20, -1, true, 0, 5);
		checkRange(20, Integer.MIN_VALUE, true, 0, 5);
		checkRange(20, 20, true, 0, 5);
		checkRange(20, Integer.MAX_VALUE, true, 0, 5);
		checkRange(-1, -1, true, 0, 0);
		checkRange(Integer.MAX_VALUE, Integer.MAX_VALUE - 1, true,
				Integer.MAX_VALUE - 5, Integer.MAX_VALUE);
		checkRange(20, 10, false, 0, 20);
		checkRange(20, -1, false, 0, 20);

		for (int count = 0; count <= 100; count++) {
			for (int selected = -1; selected <= count; selected++) {
				var range = CompactStandHotbarWindow.visibleRange(count, selected, true);
				check(range.startInclusive() >= 0 && range.endExclusive() <= count,
						"window escaped the original hotbar");
				check(range.endExclusive() - range.startInclusive() == Math.min(count, 5),
						"window size changed around selection");
				if (selected >= 0 && selected < count) {
					check(selected >= range.startInclusive() && selected < range.endExclusive(),
							"real selected index was excluded");
				}
				else {
					check(range.startInclusive() == 0, "missing selection must show the first window");
				}
				checkRange(count, selected, false, 0, count);
			}
		}
	}

	private static void checkRange(int count, int selected, boolean compact, int start, int end) {
		var actual = CompactStandHotbarWindow.visibleRange(count, selected, compact);
		check(actual.startInclusive() == start && actual.endExclusive() == end,
				"unexpected window for count=" + count + ", selected=" + selected + ", compact=" + compact);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private CompactStandHotbarWindowSmokeTest() {}
}

package rotp.core.client.ui.hud_power;

public final class StandRangeLayoutSmokeTest {
    private static int checks;

    private StandRangeLayoutSmokeTest() {}

    public static void main(String[] args) {
        verifyWideParity();
        verifyBoundary();
        verifyCompactLabels();
        verifyResizeAndTextGrowth();
        System.out.println("Stand range layout smoke test passed (" + checks + " checks).");
    }

    private static StandRangeLayout place(int viewport, int distance, int strength, int percentage) {
        return StandRangeLayout.place(303, 16, viewport, 9, distance, strength, percentage);
    }

    private static void verifyWideParity() {
        for (int viewport : new int[] {427, 480, 640}) {
            StandRangeLayout oneLine = place(viewport, 38, -1, 0);
            check(!oneLine.compact() && oneLine.x() == 303 && oneLine.y() == 16, "wide distance moved");
            check(oneLine.width() == 38 && oneLine.height() == 9, "wide distance bounds changed");
            StandRangeLayout twoLines = place(viewport, 38, 112, 40);
            check(!twoLines.compact() && !twoLines.percentageOnly(), "wide strength layout changed");
            check(twoLines.x() == 303 && twoLines.y() == 16 && twoLines.width() == 112 && twoLines.height() == 21,
                    "wide two-line bounds changed");
            check(twoLines.strengthX(38) == 303 && twoLines.strengthY() == 28, "wide strength line moved");
        }
    }

    private static void verifyBoundary() {
        check(!place(427, 38, 124, 40).compact(), "exact viewport edge should retain the preferred layout");
        check(place(427, 38, 125, 40).compact(), "one-pixel overflow did not compact");
        check(!place(320, 17, -1, 0).compact(), "fitting short distance moved");
        check(place(320, 18, -1, 0).compact(), "narrow distance overflow did not compact");
    }

    private static void verifyCompactLabels() {
        StandRangeLayout oneLine = place(320, 38, -1, 0);
        check(oneLine.compact() && !oneLine.percentageOnly(), "distance-only compact flags");
        check(oneLine.x() == 278 && oneLine.width() == 38 && oneLine.height() == 9, "distance-only compact bounds");
        verifyTopBand(oneLine);
        StandRangeLayout labeled = place(320, 38, 112, 40);
        check(labeled.compact() && !labeled.percentageOnly(), "fitting localized strength label was dropped");
        check(labeled.x() == 162 && labeled.width() == 154, "inline localized bounds");
        check(labeled.strengthX(38) == 204 && labeled.strengthY() == 3, "inline strength coordinates");
        check(labeled.strengthX(38) + 112 == labeled.x() + labeled.width(), "inline bbox misses strength");
        verifyTopBand(labeled);
        StandRangeLayout percent = place(320, 70, 400, 40);
        check(percent.percentageOnly() && percent.width() == 114 && percent.x() == 202, "percentage fallback bounds");
        check(percent.strengthX(70) + 40 == 316, "percentage did not retain four-pixel margin");
        verifyTopBand(percent);
        check(!place(320, 70, 238, 40).percentageOnly(), "exact compact width dropped full label");
        check(place(320, 70, 239, 40).percentageOnly(), "oversized compact label was not shortened");
    }

    private static void verifyTopBand(StandRangeLayout layout) {
        check(layout.y() == 3 && layout.height() == 9, "compact row must stay above the header");
        check(layout.y() + layout.height() <= 12, "compact row overlaps resolve/power icons");
        check(layout.y() + layout.height() < 16 && layout.y() + layout.height() < 28,
                "compact row overlaps stamina or energy");
        check(layout.x() >= 4 && layout.x() + layout.width() <= 316, "compact row outside viewport");
    }

    private static void verifyResizeAndTextGrowth() {
        StandRangeLayout initial = place(480, 38, 112, 40);
        StandRangeLayout narrow = place(320, 38, 112, 40);
        StandRangeLayout grown = place(320, 70, 112, 40);
        check(grown.x() < narrow.x() && grown.x() + grown.width() == narrow.x() + narrow.width(),
                "text growth did not keep right edge fixed");
        check(place(480, 38, 112, 40).equals(initial), "resize did not recover preferred position");
        check(initial.x() == 303 && initial.y() == 16, "preferred offsets mutated");
        StandRangeLayout restored = place(320, 38, -1, 0);
        check(restored.width() == 38 && restored.height() == 9 && !restored.percentageOnly(),
                "removing strength retained stale dimensions");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}

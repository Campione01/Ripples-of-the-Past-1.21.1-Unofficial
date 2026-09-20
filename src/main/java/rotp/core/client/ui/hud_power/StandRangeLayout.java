package rotp.core.client.ui.hud_power;

record StandRangeLayout(int x, int y, int width, int height, boolean compact, boolean percentageOnly) {
    private static final int MARGIN = 4;
    static final int INLINE_GAP = 4;
    static final int LINE_STEP = 12;

    static StandRangeLayout place(int preferredX, int preferredY, int viewportWidth, int lineHeight,
            int distanceWidth, int strengthWidth, int percentageWidth) {
        boolean hasStrength = strengthWidth >= 0;
        int normalWidth = Math.max(distanceWidth, strengthWidth);
        if (preferredX >= 0 && preferredX + normalWidth <= viewportWidth) {
            return new StandRangeLayout(preferredX, preferredY, normalWidth,
                    lineHeight + (hasStrength ? LINE_STEP : 0), false, false);
        }
        int fullWidth = distanceWidth + (hasStrength ? INLINE_GAP + strengthWidth : 0);
        boolean percentageOnly = hasStrength && fullWidth > viewportWidth - MARGIN * 2;
        int width = percentageOnly ? distanceWidth + INLINE_GAP + percentageWidth : fullWidth;
        return new StandRangeLayout(Math.max(MARGIN, viewportWidth - MARGIN - width),
                Math.max(0, preferredY - lineHeight - MARGIN), width, lineHeight, true, percentageOnly);
    }

    int strengthX(int distanceWidth) {
        return compact ? x + distanceWidth + INLINE_GAP : x;
    }

    int strengthY() {
        return compact ? y : y + LINE_STEP;
    }
}

package rotp.core.mechanics.clothes.sewing.client;

record SewingScreenFit(float scale, double offsetX, double offsetY) {
    static final SewingScreenFit IDENTITY = new SewingScreenFit(1.0F, 0.0, 0.0);
    private static final int MARGIN = 4;

    static SewingScreenFit forBounds(int screenWidth, int screenHeight,
            int left, int top, int contentWidth, int contentHeight) {
        if (left >= MARGIN && top >= MARGIN
                && left + contentWidth <= screenWidth - MARGIN
                && top + contentHeight <= screenHeight - MARGIN) {
            return IDENTITY;
        }
        float scale = Math.min(1.0F, Math.min(
                Math.max(1, screenWidth - MARGIN * 2) / (float) contentWidth,
                Math.max(1, screenHeight - MARGIN * 2) / (float) contentHeight));
        return new SewingScreenFit(scale,
                (screenWidth - contentWidth * (double) scale) / 2.0 - left * (double) scale,
                (screenHeight - contentHeight * (double) scale) / 2.0 - top * (double) scale);
    }

    boolean isIdentity() {
        return equals(IDENTITY);
    }

    double toScreenX(double x) { return x * scale + offsetX; }
    double toScreenY(double y) { return y * scale + offsetY; }
    double toLayoutX(double x) { return (x - offsetX) / scale; }
    double toLayoutY(double y) { return (y - offsetY) / scale; }
    double toLayoutDelta(double delta) { return delta / scale; }

    int tooltipX(int x, int tooltipWidth, int screenWidth) {
        return clampTooltip(toScreenX(x), tooltipWidth, screenWidth);
    }

    int tooltipY(int y, int tooltipHeight, int screenHeight) {
        return clampTooltip(toScreenY(y), tooltipHeight, screenHeight);
    }

    private static int clampTooltip(double position, int size, int screenSize) {
        return Math.max(MARGIN, Math.min((int) Math.floor(position), screenSize - size - MARGIN));
    }
}

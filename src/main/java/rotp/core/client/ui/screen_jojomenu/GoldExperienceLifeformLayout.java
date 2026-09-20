package rotp.core.client.ui.screen_jojomenu;

public record GoldExperienceLifeformLayout(
        int titleY, int searchY, int choiceTopY, int selectedY,
        int toolsY, int doneY, int rowsPerColumn, int columns,
        int headerLeft, int filterWidth) {

    public static GoldExperienceLifeformLayout forScreen(int width, int height, boolean grid) {
        int titleY = Math.max(8, Math.min(height / 2 - 96, height - 254));
        int doneY = Math.min(height / 2 + 130, height - 26);
        int toolsY = doneY - 24;
        int selectedY = toolsY - 16;
        int choiceTopY = titleY + (grid ? 78 : 74);
        // Keep every choice above the footer; smaller windows use more pages, not clipped rows.
        int rows = Math.max(1, Math.min(grid ? 4 : 5, (selectedY - choiceTopY) / 22));
        int filterWidth = Math.min(80, (width - 16 - 12 - 62) / 3);
        int headerWidth = filterWidth * 3 + 12 + 62;
        int headerLeft = Math.max(8, Math.min(width / 2 - 125, width - 8 - headerWidth));
        return new GoldExperienceLifeformLayout(titleY, titleY + 20, choiceTopY,
                selectedY, toolsY, doneY, rows, grid ? 4 : 2, headerLeft, filterWidth);
    }

    public int pageSize() {
        return rowsPerColumn * columns;
    }

    public int choiceY(int slot) {
        int row = columns == 4 ? slot / columns : slot % rowsPerColumn;
        return choiceTopY + row * 22;
    }
}

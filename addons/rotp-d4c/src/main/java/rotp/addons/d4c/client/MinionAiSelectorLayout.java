package rotp.addons.d4c.client;

/**
 * The 1.16 minion AI selector's layout and texts (015 com/mrlang/rotp_d4c/ad/ad/ab.java), kept free of game classes so
 * the module's contract can check it. Button index i is the minion mode i (D4CMinionAiMode).
 */
public final class MinionAiSelectorLayout {
    public static final String TITLE_KEY = "screen.rotp_d4c.minion_ai_selector";
    public static final String[] MODE_KEYS = {
            "gui.rotp_d4c.minion_ai_selector.mode.ranged",
            "gui.rotp_d4c.minion_ai_selector.mode.melee",
            "gui.rotp_d4c.minion_ai_selector.mode.wait",
            "gui.rotp_d4c.minion_ai_selector.mode.follow",
            "gui.rotp_d4c.minion_ai_selector.mode.stand_attack",
            "gui.rotp_d4c.minion_ai_selector.mode.smart_combat"};
    public static final String[] DESC_KEYS = {
            "gui.rotp_d4c.minion_ai_selector.desc.ranged",
            "gui.rotp_d4c.minion_ai_selector.desc.melee",
            "gui.rotp_d4c.minion_ai_selector.desc.wait",
            "gui.rotp_d4c.minion_ai_selector.desc.follow",
            "gui.rotp_d4c.minion_ai_selector.desc.stand_attack",
            "gui.rotp_d4c.minion_ai_selector.desc.smart_combat"};
    public static final int BUTTONS = MODE_KEYS.length;
    // ab.java:25-28
    public static final int BUTTON_SIZE = 26;
    public static final int SPACING = 30;
    public static final int PER_ROW = 5;
    // the button that shows D4C's icon instead of an item (ab.java:168-170)
    public static final int STAND_ICON_BUTTON = 4;
    // ab.java:126: the title's centre, 60 above the screen's
    public static final int TITLE_ABOVE_CENTRE = 60;
    // ab.java:161-167: fill and border, hovered or not
    public static final int FILL = 0x80000000;
    public static final int FILL_HOVERED = 0x80FFFFFF;
    public static final int BORDER = 0xFF8B8B8B;
    public static final int BORDER_HOVERED = 0xFFFFFFFF;
    public static final int ICON_INSET = 5;

    private MinionAiSelectorLayout() {}

    /** ab.java:55-117: the top-left corner {x, y} of each button on a screen of that size. */
    public static int[][] buttonPositions(int width, int height) {
        int rows = (BUTTONS + PER_ROW - 1) / PER_ROW;
        int gridWidth = PER_ROW * SPACING - (SPACING - BUTTON_SIZE);
        int gridHeight = rows * BUTTON_SIZE + (rows - 1) * (SPACING - BUTTON_SIZE);
        if (width <= 0 || height <= 0) {
            return new int[0][];
        }
        boolean small = width < gridWidth + 40 || height < gridHeight + 140;
        int startX = small ? Math.max(10, (width - gridWidth) / 2) : (width - gridWidth) / 2;
        int startY = small ? Math.max(60, (height - gridHeight) / 2) : (height - gridHeight) / 2;
        int[][] positions = new int[BUTTONS][];
        for (int i = 0; i < BUTTONS; i++) {
            int x = startX + i % PER_ROW * SPACING;
            int y = startY + i / PER_ROW * SPACING;
            x = Math.max(Math.min(x, width - BUTTON_SIZE - 5), 5);
            y = Math.max(Math.min(y, height - BUTTON_SIZE - 5), 5);
            positions[i] = new int[] {x, y};
        }
        return positions;
    }
}

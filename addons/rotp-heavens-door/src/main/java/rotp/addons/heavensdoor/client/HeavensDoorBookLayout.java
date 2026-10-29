package rotp.addons.heavensdoor.client;

import java.util.List;

public final class HeavensDoorBookLayout {
    public static final int IMAGE_WIDTH = 262;
    public static final int IMAGE_HEIGHT = 165;

    public static final Area LEFT_PAGE_FILL =
            new Area(9, 9, 116, 145);
    public static final Area RIGHT_PAGE_FILL =
            new Area(143, 9, 108, 145);

    public static final List<Area> LEFT_FIELDS = List.of(
            new Area(10, 10, 114, 13),
            new Area(10, 23, 114, 20),
            new Area(10, 43, 114, 13),
            new Area(10, 56, 114, 13),
            new Area(10, 69, 114, 13),
            new Area(10, 82, 114, 15));

    public static final List<Area> RIGHT_FIELDS = List.of(
            new Area(144, 10, 106, 24),
            new Area(144, 34, 106, 24),
            new Area(144, 58, 106, 18),
            new Area(144, 76, 106, 21));

    public static final List<Area> LEFT_COMMANDS = List.of(
            new Area(12, 105, 110, 17),
            new Area(12, 132, 110, 17));

    public static final List<Area> RIGHT_COMMANDS = List.of(
            new Area(145, 100, 104, 12),
            new Area(145, 114, 104, 12),
            new Area(145, 128, 104, 12),
            new Area(145, 142, 104, 12));

    private HeavensDoorBookLayout() {}

    public record Area(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(Area other) {
            return other.x >= x
                    && other.y >= y
                    && other.right() <= right()
                    && other.bottom() <= bottom();
        }
    }
}

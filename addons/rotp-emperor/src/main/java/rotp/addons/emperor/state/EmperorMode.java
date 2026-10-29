package rotp.addons.emperor.state;

public enum EmperorMode {
    NONE,
    HOSTILE,
    PLAYERS_AND_STANDS,
    ANY_LIVING;

    public EmperorMode cycle(boolean backwards) {
        EmperorMode[] values = values();
        int offset = backwards ? values.length - 1 : 1;
        return values[(ordinal() + offset) % values.length];
    }

    public static EmperorMode byOrdinal(int ordinal) {
        EmperorMode[] values = values();
        return ordinal >= 0 && ordinal < values.length
                ? values[ordinal]
                : NONE;
    }
}

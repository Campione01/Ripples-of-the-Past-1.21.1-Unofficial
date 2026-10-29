package rotp.addons.sexpistols.content.sexpistols;

public enum SexPistolsTransferOrder {
    NONE,
    DISTANCE,
    NUMBER;

    public static SexPistolsTransferOrder byId(int id) {
        SexPistolsTransferOrder[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    public SexPistolsTransferOrder cycle(boolean backwards) {
        return byId(ordinal() + (backwards ? -1 : 1));
    }
}

package rotp.addons.sexpistols.content.sexpistols;

public enum SexPistolsMemberState {
    FORMATION,
    SCOUTING,
    RECALLING,
    ENCIRCLEMENT;

    public static SexPistolsMemberState byId(int id) {
        SexPistolsMemberState[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}

package rotp.addons.yellowtemperance.disguise;

public enum DisguiseMode {
    NONE(0),
    ENTITY(1),
    PLAYER(2),
    STAND(3);

    private final byte id;

    DisguiseMode(int id) {
        this.id = (byte) id;
    }

    public byte id() {
        return id;
    }

    public static DisguiseMode byId(int id) {
        for (DisguiseMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return NONE;
    }
}

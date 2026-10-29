package rotp.addons.pearljam.food;

import com.mojang.serialization.Codec;

public enum PearlJamDishKind {
    INJECTED("injected"),
    POISONED("poisoned");

    public static final Codec<PearlJamDishKind> CODEC =
            Codec.STRING.xmap(
                    PearlJamDishKind::bySerializedName,
                    PearlJamDishKind::serializedName);

    private final String serializedName;

    PearlJamDishKind(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PearlJamDishKind bySerializedName(String name) {
        for (PearlJamDishKind kind : values()) {
            if (kind.serializedName.equals(name)) {
                return kind;
            }
        }
        return INJECTED;
    }

    public static PearlJamDishKind byId(int id) {
        PearlJamDishKind[] values = values();
        return id >= 0 && id < values.length
                ? values[id]
                : INJECTED;
    }
}

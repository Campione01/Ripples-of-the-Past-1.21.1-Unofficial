package rotp.addons.pearljam.food;

import com.mojang.serialization.Codec;

public enum PearlJamTreatment {
    NONE("none"),
    RESTORATION("restoration"),
    DETOX("detox");

    public static final Codec<PearlJamTreatment> CODEC =
            Codec.STRING.xmap(
                    PearlJamTreatment::bySerializedName,
                    PearlJamTreatment::serializedName);

    private final String serializedName;

    PearlJamTreatment(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "rotp_pj.treatment." + serializedName;
    }

    public boolean isActive() {
        return this != NONE;
    }

    public static PearlJamTreatment bySerializedName(String name) {
        for (PearlJamTreatment treatment : values()) {
            if (treatment.serializedName.equals(name)) {
                return treatment;
            }
        }
        return NONE;
    }

    public static PearlJamTreatment byId(int id) {
        PearlJamTreatment[] values = values();
        return id >= 0 && id < values.length
                ? values[id]
                : NONE;
    }
}

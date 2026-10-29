package rotp.addons.tinkers_of_the_past;

/**
 * Dependency-free classification of the optional Mantle/TConstruct runtime.
 */
public enum TConstructRuntimeState {
    ABSENT(false, false),
    MANTLE_ONLY(true, false),
    TCONSTRUCT_ONLY(false, true),
    COMPLETE_BLOCKED_API_UNAVAILABLE(true, true);

    private final boolean mantlePresent;
    private final boolean tconstructPresent;

    TConstructRuntimeState(
            boolean mantlePresent, boolean tconstructPresent) {
        this.mantlePresent = mantlePresent;
        this.tconstructPresent = tconstructPresent;
    }

    public static TConstructRuntimeState fromPresence(
            boolean mantlePresent, boolean tconstructPresent) {
        if (mantlePresent && tconstructPresent) {
            return COMPLETE_BLOCKED_API_UNAVAILABLE;
        }
        if (mantlePresent) {
            return MANTLE_ONLY;
        }
        if (tconstructPresent) {
            return TCONSTRUCT_ONLY;
        }
        return ABSENT;
    }

    public boolean mantlePresent() {
        return mantlePresent;
    }

    public boolean tconstructPresent() {
        return tconstructPresent;
    }

    public boolean completeRuntimePresent() {
        return mantlePresent && tconstructPresent;
    }

    public boolean modifierIntegrationAvailable() {
        return false;
    }
}

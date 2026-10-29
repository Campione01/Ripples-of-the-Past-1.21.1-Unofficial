package rotp.addons.waytoheaven.state;

public final class HeavenEvolutionState {
    private boolean fallImmunity;

    public void grantFallImmunity() {
        fallImmunity = true;
    }

    public boolean hasFallImmunity() {
        return fallImmunity;
    }

    public boolean consumeFallImmunity() {
        if (!fallImmunity) {
            return false;
        }
        fallImmunity = false;
        return true;
    }

    public void clear() {
        fallImmunity = false;
    }
}

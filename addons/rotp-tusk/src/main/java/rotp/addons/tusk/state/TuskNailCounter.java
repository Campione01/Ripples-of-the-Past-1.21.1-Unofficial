package rotp.addons.tusk.state;

public class TuskNailCounter {
    public static final int MAX_NAILS = 10;
    public static final int REGEN_TICKS = 50;
    public static final int MAX_REGEN_TICKS = REGEN_TICKS * 4;

    private int nails = MAX_NAILS;
    private int regenTicksRemaining;

    public int nails() {
        return nails;
    }

    public int regenTicksRemaining() {
        return regenTicksRemaining;
    }

    public boolean consumeNail() {
        return consumeNail(REGEN_TICKS);
    }

    public boolean consumeNail(int regenTicks) {
        if (nails <= 0) {
            return false;
        }
        nails--;
        regenTicksRemaining = clamp(regenTicks, REGEN_TICKS, MAX_REGEN_TICKS);
        return true;
    }

    public boolean addNail() {
        return addNail(REGEN_TICKS);
    }

    public boolean addNail(int regenTicks) {
        if (nails >= MAX_NAILS) {
            regenTicksRemaining = 0;
            return false;
        }
        nails++;
        if (nails >= MAX_NAILS) {
            regenTicksRemaining = 0;
        } else if (regenTicksRemaining <= 0) {
            regenTicksRemaining = clamp(regenTicks, REGEN_TICKS, MAX_REGEN_TICKS);
        }
        return true;
    }

    public boolean tickServer() {
        return tickServer(REGEN_TICKS);
    }

    public boolean tickServer(int regenTicks) {
        int period = clamp(regenTicks, REGEN_TICKS, MAX_REGEN_TICKS);
        if (nails >= MAX_NAILS) {
            if (regenTicksRemaining != 0) {
                regenTicksRemaining = 0;
                return true;
            }
            return false;
        }

        if (regenTicksRemaining <= 0) {
            regenTicksRemaining = period;
        }
        regenTicksRemaining--;
        if (regenTicksRemaining > 0) {
            return false;
        }

        nails++;
        regenTicksRemaining = nails < MAX_NAILS ? period : 0;
        return true;
    }

    public boolean reset() {
        boolean changed = nails != MAX_NAILS || regenTicksRemaining != 0;
        nails = MAX_NAILS;
        regenTicksRemaining = 0;
        return changed;
    }

    public void copyFrom(TuskNailCounter other) {
        applySnapshot(other.nails, other.regenTicksRemaining);
    }

    public void applySnapshot(int nails, int regenTicksRemaining) {
        this.nails = clamp(nails, 0, MAX_NAILS);
        this.regenTicksRemaining = this.nails >= MAX_NAILS
                ? 0
                : clamp(regenTicksRemaining, 0, MAX_REGEN_TICKS);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

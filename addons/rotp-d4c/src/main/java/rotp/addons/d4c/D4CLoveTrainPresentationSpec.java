package rotp.addons.d4c;

public final class D4CLoveTrainPresentationSpec {
    public static final int GATHER_END_TICK = 260;
    public static final int CONVERGENCE_END_TICK = 320;
    public static final int PILLARS_END_TICK = 460;
    public static final int FINALE_EMISSION_END_TICK = 535;
    public static final int COMMIT_TICK = 540;
    public static final int TOTAL_TICKS = 560;
    public static final int POST_COMMIT_TICKS =
            TOTAL_TICKS - COMMIT_TICK;

    private D4CLoveTrainPresentationSpec() {}

    public static Phase phaseAt(int tick) {
        if (tick < 0) {
            return Phase.IDLE;
        }
        if (tick <= GATHER_END_TICK) {
            return Phase.GATHER;
        }
        if (tick <= CONVERGENCE_END_TICK) {
            return Phase.CONVERGENCE;
        }
        if (tick <= PILLARS_END_TICK) {
            return Phase.PILLARS;
        }
        if (tick < TOTAL_TICKS) {
            return Phase.FINALE;
        }
        return Phase.COMPLETE;
    }

    public static boolean emitsAt(int tick) {
        return tick > 0
                && tick <= FINALE_EMISSION_END_TICK;
    }

    public enum Phase {
        IDLE,
        GATHER,
        CONVERGENCE,
        PILLARS,
        FINALE,
        COMPLETE
    }
}

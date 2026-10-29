package rotp.addons.echoes.state;

public final class EchoesFreezeTiming {
    public static final long DURATION_TICKS = 600L;

    private EchoesFreezeTiming() {}

    public static long deadlineAfter(long gameTime) {
        return gameTime + DURATION_TICKS;
    }

    public static boolean expired(long deadline, long gameTime) {
        return deadline > 0L && gameTime >= deadline;
    }

    public static long initializeLegacyDeadline(
            long deadline, long gameTime, boolean activeTargetOrHand) {
        return activeTargetOrHand && deadline == 0L
                ? deadlineAfter(gameTime) : deadline;
    }
}

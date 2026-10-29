package rotp.addons.d4c.travel;

/**
 * Numbers and gates of the 1.16 parallel-world terrain copy
 * (1.16 am.java:231-438). Pure Java so the contract check can call it.
 */
public final class D4CParallelMirrorRules {
    /** Chunks copied on each side of the arrival chunk (9x9). */
    public static final int CHUNK_RADIUS = 4;
    /** Blocks copied below the arrival feet. */
    public static final int BELOW = 16;
    /** Blocks copied above the arrival feet. */
    public static final int ABOVE = 48;
    /** Per-tick write budget of the throttled job. */
    public static final int BLOCKS_PER_TICK = 500;
    public static final long NANOS_PER_TICK = 8_000_000L;
    public static final int CHUNKS_PER_TICK = 4;
    /** Every second chunk finished in a tick updates its border ring. */
    public static final int NEIGHBOUR_UPDATE_EVERY = 2;

    private D4CParallelMirrorRules() {}

    /** Only overworld to parallel world arrivals copy terrain. */
    public static boolean shouldMirror(
            boolean fromOverworld,
            boolean toParallel) {
        return fromOverworld && toParallel;
    }

    public static int lowestY(int feetY, int minBuildHeight) {
        return Math.max(minBuildHeight, feetY - BELOW);
    }

    /** maxBuildHeight is exclusive. */
    public static int highestY(int feetY, int maxBuildHeight) {
        return Math.min(maxBuildHeight - 1, feetY + ABOVE);
    }

    public static boolean withinTickBudget(
            int written,
            int finishedChunks,
            long elapsedNanos) {
        return written < BLOCKS_PER_TICK
                && finishedChunks < CHUNKS_PER_TICK
                && elapsedNanos < NANOS_PER_TICK;
    }

    /** Portal blocks are not copied; they become air. */
    public static <S> S mirroredState(S overworld, boolean portal, S air) {
        return portal ? air : overworld;
    }

    /** Blocks already equal in the parallel world are skipped. */
    public static boolean needsWrite(Object target, Object current) {
        return target != null && !target.equals(current);
    }

    /** Arrival chunk first, then nearer chunks. */
    public static long chunkOrder(int dx, int dz) {
        return (long) dx * dx + (long) dz * dz;
    }

    public static boolean updatesNeighboursAfter(int finishedThisTick) {
        return finishedThisTick % NEIGHBOUR_UPDATE_EVERY == 0;
    }
}

package com.github.standobyte.jojo.subsystems.timestop;

public final class TimeStopClientAwareness {
    private static boolean initialized;
    private static boolean canSee = true;
    private static boolean canMove = true;
    private static boolean enterTransitionPending;
    private static boolean movementBlockTransitionPending;
    private static boolean restoreTransitionPending;

    private TimeStopClientAwareness() {}

    public static void apply(boolean canSee, boolean canMove) {
        boolean wasRestrictive = isRestrictive();
        boolean wasMoveBlocked = isMoveBlocked();
        initialized = true;
        TimeStopClientAwareness.canSee = canSee;
        TimeStopClientAwareness.canMove = canMove;
        boolean restrictiveNow = isRestrictive();
        enterTransitionPending = !wasRestrictive && restrictiveNow;
        movementBlockTransitionPending = !wasMoveBlocked && isMoveBlocked();
        restoreTransitionPending = wasRestrictive && !restrictiveNow;
    }

    public static void reset() {
        boolean wasRestrictive = isRestrictive();
        initialized = false;
        canSee = true;
        canMove = true;
        enterTransitionPending = false;
        movementBlockTransitionPending = false;
        restoreTransitionPending = wasRestrictive;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean canSee() {
        return initialized ? canSee : true;
    }

    public static boolean canMove() {
        return initialized ? canMove : true;
    }

    public static boolean isRestrictive() {
        return !canMove() || !canSee();
    }

    public static boolean isMoveBlocked() {
        return !canMove();
    }

    public static boolean isVisionRestricted() {
        return isRestrictive() && !canSee();
    }

    public static boolean consumeEnterTransition() {
        boolean pending = enterTransitionPending;
        enterTransitionPending = false;
        return pending;
    }

    public static boolean consumeMovementBlockTransition() {
        boolean pending = movementBlockTransitionPending;
        movementBlockTransitionPending = false;
        return pending;
    }

    public static boolean consumeRestoreTransition() {
        boolean pending = restoreTransitionPending;
        restoreTransitionPending = false;
        return pending;
    }
}

package rotp.addons.stickyfingers.state;

public final class ZipperRestorationPolicy {
    public enum ObservedState {
        OWNED_ZIPPER,
        ORIGINAL_SNAPSHOT,
        FOREIGN_BLOCK
    }

    public enum Decision {
        RESTORE_SNAPSHOT,
        RESTORE_BLOCK_ENTITY,
        FINALIZE_WITHOUT_OVERWRITE
    }

    private ZipperRestorationPolicy() {}

    public static boolean canAttempt(
            boolean pendingRestore, boolean chunkLoaded) {
        return pendingRestore && chunkLoaded;
    }

    public static Decision decideLoaded(
            ObservedState observedState) {
        return switch (observedState) {
            case OWNED_ZIPPER -> Decision.RESTORE_SNAPSHOT;
            case ORIGINAL_SNAPSHOT ->
                    Decision.RESTORE_BLOCK_ENTITY;
            case FOREIGN_BLOCK ->
                    Decision.FINALIZE_WITHOUT_OVERWRITE;
        };
    }
}

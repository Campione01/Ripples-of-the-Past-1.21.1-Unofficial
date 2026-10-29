package rotp.addons.mandom;

import java.util.Set;

import rotp.core.api.rollback.RollbackCapability;
import rotp.core.api.rollback.RollbackSupportMatrix;

/**
 * Public timing constants and the explicit module-local transaction gate.
 */
public final class MandomRollbackGate {
    public static final int REWIND_TICKS = 6 * 20;
    public static final int COOLDOWN_TICKS = 6 * 20;
    public static final int RANGE_CHUNKS = 10;
    public static final int RANGE_BLOCKS = RANGE_CHUNKS * 16;
    public static final boolean REWIND_EXPOSED = true;

    private MandomRollbackGate() {}

    public static boolean coreSupportsAtomicRewind() {
        return RollbackSupportMatrix.isTransactionAvailable();
    }

    public static boolean moduleSupportsAtomicRewind() {
        return true;
    }

    public static Set<RollbackCapability> blockers() {
        return RollbackSupportMatrix.unsupportedRequiredCapabilities();
    }
}

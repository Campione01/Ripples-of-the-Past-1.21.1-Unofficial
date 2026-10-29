package rotp.addons.tusk.diagnostic;

import java.util.UUID;

import javax.annotation.Nullable;

public final class TuskActionDiagnostics {
    private static long serverPerformTicks;
    private static long clientPerformTicks;
    private static long serverImpactCount;
    @Nullable private static UUID lastStandUuid;
    @Nullable private static UUID lastUserUuid;
    @Nullable private static UUID lastImpactStandUuid;
    @Nullable private static UUID lastImpactSourceUuid;
    @Nullable private static UUID lastImpactTargetUuid;

    private TuskActionDiagnostics() {}

    public static synchronized void reset() {
        serverPerformTicks = 0L;
        clientPerformTicks = 0L;
        serverImpactCount = 0L;
        lastStandUuid = null;
        lastUserUuid = null;
        lastImpactStandUuid = null;
        lastImpactSourceUuid = null;
        lastImpactTargetUuid = null;
    }

    public static synchronized void recordPerformTick(
            boolean clientSide,
            UUID standUuid,
            @Nullable UUID userUuid) {
        if (clientSide) {
            clientPerformTicks++;
        } else {
            serverPerformTicks++;
        }
        lastStandUuid = standUuid;
        lastUserUuid = userUuid;
    }

    public static synchronized void recordImpact(
            UUID standUuid,
            @Nullable UUID sourceUuid,
            UUID targetUuid) {
        serverImpactCount++;
        lastImpactStandUuid = standUuid;
        lastImpactSourceUuid = sourceUuid;
        lastImpactTargetUuid = targetUuid;
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                serverPerformTicks,
                clientPerformTicks,
                serverImpactCount,
                lastStandUuid,
                lastUserUuid,
                lastImpactStandUuid,
                lastImpactSourceUuid,
                lastImpactTargetUuid);
    }

    public record Snapshot(
            long serverPerformTicks,
            long clientPerformTicks,
            long serverImpactCount,
            @Nullable UUID lastStandUuid,
            @Nullable UUID lastUserUuid,
            @Nullable UUID lastImpactStandUuid,
            @Nullable UUID lastImpactSourceUuid,
            @Nullable UUID lastImpactTargetUuid) {}
}

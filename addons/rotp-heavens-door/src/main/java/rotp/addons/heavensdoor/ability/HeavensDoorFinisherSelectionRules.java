package rotp.addons.heavensdoor.ability;

import java.util.Objects;
import java.util.UUID;

public final class HeavensDoorFinisherSelectionRules {
    private HeavensDoorFinisherSelectionRules() {}

    public static boolean clientSnapshotAuthorizes(
            boolean active,
            UUID snapshotOwnerUuid,
            UUID snapshotTargetUuid,
            String snapshotDimension,
            long snapshotExpiresAt,
            UUID ownerUuid,
            UUID targetUuid,
            String targetDimension,
            long gameTime) {
        return active
                && ownerUuid != null
                && targetUuid != null
                && targetDimension != null
                && Objects.equals(snapshotOwnerUuid, ownerUuid)
                && Objects.equals(snapshotTargetUuid, targetUuid)
                && Objects.equals(snapshotDimension, targetDimension)
                && gameTime <= snapshotExpiresAt;
    }
}

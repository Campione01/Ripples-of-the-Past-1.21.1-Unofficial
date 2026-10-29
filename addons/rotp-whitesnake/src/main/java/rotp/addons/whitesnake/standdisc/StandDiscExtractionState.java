package rotp.addons.whitesnake.standdisc;

import java.util.Optional;
import java.util.UUID;

public final class StandDiscExtractionState {
    private UUID extractionId;
    private String standId;

    public boolean isActive() {
        return extractionId != null && standId != null;
    }

    public Optional<UUID> extractionId() {
        return Optional.ofNullable(extractionId);
    }

    public Optional<String> standId() {
        return Optional.ofNullable(standId);
    }

    public boolean begin(UUID nextExtractionId, String nextStandId) {
        if (isActive()
                || nextExtractionId == null
                || nextStandId == null
                || nextStandId.isBlank()) {
            return false;
        }
        extractionId = nextExtractionId;
        standId = nextStandId;
        return true;
    }

    public boolean matches(
            UUID expectedExtractionId, String expectedStandId) {
        return isActive()
                && extractionId.equals(expectedExtractionId)
                && standId.equals(expectedStandId);
    }

    public boolean restore(
            UUID expectedExtractionId, String expectedStandId) {
        if (!matches(expectedExtractionId, expectedStandId)) {
            return false;
        }
        clear();
        return true;
    }

    public void copyFrom(StandDiscExtractionState source) {
        if (source != null && source.isActive()) {
            extractionId = source.extractionId;
            standId = source.standId;
        } else {
            clear();
        }
    }

    public void load(UUID loadedExtractionId, String loadedStandId) {
        clear();
        begin(loadedExtractionId, loadedStandId);
    }

    private void clear() {
        extractionId = null;
        standId = null;
    }
}

package rotp.addons.whitesnake.memory;

import java.util.Optional;
import java.util.UUID;

public final class MemoryExtractionState {
    private UUID extractionId;

    public boolean isActive() {
        return extractionId != null;
    }

    public Optional<UUID> extractionId() {
        return Optional.ofNullable(extractionId);
    }

    public boolean begin(UUID nextExtractionId) {
        if (extractionId != null || nextExtractionId == null) {
            return false;
        }
        extractionId = nextExtractionId;
        return true;
    }

    public boolean matches(UUID expectedExtractionId) {
        return extractionId != null
                && extractionId.equals(expectedExtractionId);
    }

    public boolean restore(UUID expectedExtractionId) {
        if (!matches(expectedExtractionId)) {
            return false;
        }
        extractionId = null;
        return true;
    }

    public void copyFrom(MemoryExtractionState source) {
        extractionId = source != null
                ? source.extractionId
                : null;
    }

    void load(UUID persistedExtractionId) {
        extractionId = persistedExtractionId;
    }
}

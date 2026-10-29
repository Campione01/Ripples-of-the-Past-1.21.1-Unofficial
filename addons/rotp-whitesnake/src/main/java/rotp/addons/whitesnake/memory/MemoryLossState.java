package rotp.addons.whitesnake.memory;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class MemoryLossState
        implements INBTSerializable<CompoundTag> {
    private static final String EXTRACTION_ID_TAG = "ExtractionId";

    private final MemoryExtractionState extraction =
            new MemoryExtractionState();

    public boolean isExtracted() {
        return extraction.isActive();
    }

    public Optional<UUID> extractionId() {
        return extraction.extractionId();
    }

    public boolean beginExtraction(UUID nextExtractionId) {
        return extraction.begin(nextExtractionId);
    }

    public boolean matches(UUID expectedExtractionId) {
        return extraction.matches(expectedExtractionId);
    }

    public boolean restore(UUID expectedExtractionId) {
        return extraction.restore(expectedExtractionId);
    }

    public void copyFrom(MemoryLossState source) {
        extraction.copyFrom(source != null ? source.extraction : null);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        extraction.extractionId().ifPresent(
                id -> tag.putUUID(EXTRACTION_ID_TAG, id));
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        extraction.load(tag.hasUUID(EXTRACTION_ID_TAG)
                ? tag.getUUID(EXTRACTION_ID_TAG)
                : null);
    }
}

package rotp.addons.whitesnake.standdisc;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class StandDiscLossState
        implements INBTSerializable<CompoundTag> {
    private static final String EXTRACTION_ID_TAG = "ExtractionId";
    private static final String STAND_ID_TAG = "StandId";
    private static final String PROGRESSION_TAG = "Progression";
    private static final String AWAKENING_TAG = "Awakening";

    private final StandDiscExtractionState extraction =
            new StandDiscExtractionState();
    private CompoundTag progression = new CompoundTag();
    private CompoundTag awakening = new CompoundTag();

    public boolean isExtracted() {
        return extraction.isActive();
    }

    public boolean beginExtraction(
            UUID extractionId,
            ResourceLocation standId,
            CompoundTag progressionSnapshot,
            CompoundTag awakeningSnapshot) {
        if (standId == null
                || progressionSnapshot == null
                || awakeningSnapshot == null
                || !extraction.begin(
                        extractionId, standId.toString())) {
            return false;
        }
        progression = progressionSnapshot.copy();
        awakening = awakeningSnapshot.copy();
        return true;
    }

    public boolean matches(
            UUID extractionId, ResourceLocation standId) {
        return standId != null
                && extraction.matches(
                        extractionId, standId.toString());
    }

    public Optional<CompoundTag> progression(
            UUID extractionId, ResourceLocation standId) {
        return matches(extractionId, standId)
                ? Optional.of(progression.copy())
                : Optional.empty();
    }

    public Optional<CompoundTag> awakening(
            UUID extractionId, ResourceLocation standId) {
        return matches(extractionId, standId)
                ? Optional.of(awakening.copy())
                : Optional.empty();
    }

    public boolean restore(
            UUID extractionId, ResourceLocation standId) {
        if (standId == null
                || !extraction.restore(
                        extractionId, standId.toString())) {
            return false;
        }
        progression = new CompoundTag();
        awakening = new CompoundTag();
        return true;
    }

    public void copyFrom(StandDiscLossState source) {
        extraction.copyFrom(
                source != null ? source.extraction : null);
        progression = source != null && source.isExtracted()
                ? source.progression.copy()
                : new CompoundTag();
        awakening = source != null && source.isExtracted()
                ? source.awakening.copy()
                : new CompoundTag();
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        extraction.extractionId().ifPresent(
                id -> tag.putUUID(EXTRACTION_ID_TAG, id));
        extraction.standId().ifPresent(
                id -> tag.putString(STAND_ID_TAG, id));
        if (extraction.isActive()) {
            tag.put(PROGRESSION_TAG, progression.copy());
            tag.put(AWAKENING_TAG, awakening.copy());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        ResourceLocation standId = tag.contains(STAND_ID_TAG)
                ? ResourceLocation.tryParse(
                        tag.getString(STAND_ID_TAG))
                : null;
        boolean complete = tag.hasUUID(EXTRACTION_ID_TAG)
                && standId != null
                && tag.get(PROGRESSION_TAG)
                        instanceof CompoundTag
                && tag.get(AWAKENING_TAG)
                        instanceof CompoundTag;
        extraction.load(
                complete
                        ? tag.getUUID(EXTRACTION_ID_TAG)
                        : null,
                complete ? standId.toString() : null);
        progression = extraction.isActive()
                ? tag.getCompound(PROGRESSION_TAG).copy()
                : new CompoundTag();
        awakening = extraction.isActive()
                ? tag.getCompound(AWAKENING_TAG).copy()
                : new CompoundTag();
    }
}

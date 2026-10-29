package rotp.addons.scr.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class RequiemSoulState
        implements INBTSerializable<CompoundTag> {
    public static final int MAX_PERSISTED_PHASE_TICKS = 200;
    public static final int MAX_PERSISTED_LINK_TICKS = 1200;
    public static final int MAX_PERSISTED_MISSING_SOURCE_TICKS = 40;
    public static final int MAX_PERSISTED_COOLDOWN_TICKS = 200;

    private static final String PHASE_TAG = "Phase";
    private static final String SOURCE_STAND_TAG = "SourceStand";
    private static final String SOURCE_OWNER_TAG = "SourceOwner";
    private static final String PARTNER_TAG = "Partner";
    private static final String PHASE_TICKS_TAG = "PhaseTicks";
    private static final String LINK_TICKS_TAG = "LinkTicks";
    private static final String MISSING_SOURCE_TAG =
            "MissingSourceTicks";
    private static final String COOLDOWN_TAG = "CooldownTicks";

    public enum Phase {
        NONE,
        SLUMBER,
        EXCHANGED
    }

    private Phase phase = Phase.NONE;
    private UUID sourceStandId;
    private UUID sourceOwnerId;
    private UUID partnerId;
    private int phaseTicks;
    private int linkTicks;
    private int missingSourceTicks;
    private int cooldownTicks;

    public Phase phase() {
        return phase;
    }

    public boolean isActive() {
        return phase != Phase.NONE;
    }

    public boolean isSlumbering() {
        return phase == Phase.SLUMBER;
    }

    public boolean isExchanged() {
        return phase == Phase.EXCHANGED;
    }

    @Nullable
    public UUID sourceStandId() {
        return sourceStandId;
    }

    @Nullable
    public UUID sourceOwnerId() {
        return sourceOwnerId;
    }

    @Nullable
    public UUID partnerId() {
        return partnerId;
    }

    public int phaseTicks() {
        return phaseTicks;
    }

    public int linkTicks() {
        return linkTicks;
    }

    public int missingSourceTicks() {
        return missingSourceTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean canEnterField() {
        return !isActive() && cooldownTicks <= 0;
    }

    public void beginSlumber(
            UUID standId, UUID ownerId) {
        if (!canEnterField()) {
            return;
        }
        phase = Phase.SLUMBER;
        sourceStandId = standId;
        sourceOwnerId = ownerId;
        partnerId = null;
        phaseTicks = 0;
        linkTicks = 0;
        missingSourceTicks = 0;
    }

    public void markExchanged(
            UUID partner, int durationTicks) {
        phase = Phase.EXCHANGED;
        partnerId = partner;
        phaseTicks = 0;
        linkTicks = Math.clamp(
                durationTicks, 1, MAX_PERSISTED_LINK_TICKS);
        missingSourceTicks = 0;
    }

    public void tickPhase() {
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (!isActive()) {
            return;
        }
        phaseTicks++;
        if (isExchanged() && linkTicks > 0) {
            linkTicks--;
        }
    }

    public void markSourcePresent() {
        missingSourceTicks = 0;
    }

    public int markSourceMissing() {
        return ++missingSourceTicks;
    }

    public void clear(int cooldown) {
        phase = Phase.NONE;
        sourceStandId = null;
        sourceOwnerId = null;
        partnerId = null;
        phaseTicks = 0;
        linkTicks = 0;
        missingSourceTicks = 0;
        cooldownTicks = Math.clamp(
                Math.max(cooldownTicks, cooldown),
                0,
                MAX_PERSISTED_COOLDOWN_TICKS);
    }

    public void copyFrom(RequiemSoulState source) {
        if (source == null) {
            return;
        }
        phase = source.phase;
        sourceStandId = source.sourceStandId;
        sourceOwnerId = source.sourceOwnerId;
        partnerId = source.partnerId;
        phaseTicks = Math.clamp(
                source.phaseTicks, 0, MAX_PERSISTED_PHASE_TICKS);
        linkTicks = Math.clamp(
                source.linkTicks, 0, MAX_PERSISTED_LINK_TICKS);
        missingSourceTicks = Math.clamp(
                source.missingSourceTicks,
                0,
                MAX_PERSISTED_MISSING_SOURCE_TICKS);
        cooldownTicks = Math.clamp(
                source.cooldownTicks,
                0,
                MAX_PERSISTED_COOLDOWN_TICKS);
        normalizeLoadedState();
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString(PHASE_TAG, phase.name());
        if (sourceStandId != null) {
            tag.putUUID(SOURCE_STAND_TAG, sourceStandId);
        }
        if (sourceOwnerId != null) {
            tag.putUUID(SOURCE_OWNER_TAG, sourceOwnerId);
        }
        if (partnerId != null) {
            tag.putUUID(PARTNER_TAG, partnerId);
        }
        tag.putInt(PHASE_TICKS_TAG, phaseTicks);
        tag.putInt(LINK_TICKS_TAG, linkTicks);
        tag.putInt(MISSING_SOURCE_TAG, missingSourceTicks);
        tag.putInt(COOLDOWN_TAG, cooldownTicks);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        try {
            phase = Phase.valueOf(tag.getString(PHASE_TAG));
        }
        catch (IllegalArgumentException ignored) {
            phase = Phase.NONE;
        }
        sourceStandId = tag.hasUUID(SOURCE_STAND_TAG)
                ? tag.getUUID(SOURCE_STAND_TAG)
                : null;
        sourceOwnerId = tag.hasUUID(SOURCE_OWNER_TAG)
                ? tag.getUUID(SOURCE_OWNER_TAG)
                : null;
        partnerId = tag.hasUUID(PARTNER_TAG)
                ? tag.getUUID(PARTNER_TAG)
                : null;
        phaseTicks = Math.clamp(
                tag.getInt(PHASE_TICKS_TAG),
                0,
                MAX_PERSISTED_PHASE_TICKS);
        linkTicks = Math.clamp(
                tag.getInt(LINK_TICKS_TAG),
                0,
                MAX_PERSISTED_LINK_TICKS);
        missingSourceTicks = Math.clamp(
                tag.getInt(MISSING_SOURCE_TAG),
                0,
                MAX_PERSISTED_MISSING_SOURCE_TICKS);
        cooldownTicks = Math.clamp(
                tag.getInt(COOLDOWN_TAG),
                0,
                MAX_PERSISTED_COOLDOWN_TICKS);
        normalizeLoadedState();
    }

    private void normalizeLoadedState() {
        if (phase == Phase.NONE) {
            sourceStandId = null;
            sourceOwnerId = null;
            partnerId = null;
            linkTicks = 0;
            return;
        }
        if (sourceStandId == null || sourceOwnerId == null) {
            clear(cooldownTicks);
            return;
        }
        if (phase == Phase.SLUMBER) {
            partnerId = null;
            linkTicks = 0;
            return;
        }
        if (partnerId == null || linkTicks <= 0) {
            clear(cooldownTicks);
        }
    }
}

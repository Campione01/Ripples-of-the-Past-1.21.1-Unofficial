package rotp.addons.heavensdoor.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.control.MobBehaviorMode;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class BookCommandState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 3;

    private static final String VERSION_TAG = "Version";
    private static final String SUBJECT_TAG = "Subject";
    private static final String COMBAT_TAG = "Combat";
    private static final String BEHAVIOR_TAG = "Behavior";
    private static final String ISSUER_TAG = "Issuer";
    private static final String FORBIDDEN_TAG = "Forbidden";
    private static final String LEASE_TAG = "Lease";
    private static final String ISSUER_EPOCH_TAG = "IssuerEpoch";
    private static final String ISSUER_DEATH_EPOCH_TAG = "IssuerDeathEpoch";
    private static final String RESTRICTED_EPOCH_TAG = "RestrictedEpoch";
    private static final String FORBIDDEN_EPOCH_TAG =
            "ForbiddenEpoch";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String MODE_TAG = "Mode";

    @Nullable
    private UUID subjectUuid;
    @Nullable
    private UUID combatIssuerUuid;
    @Nullable
    private UUID combatForbiddenUuid;
    @Nullable
    private UUID combatLeaseId;
    private long combatIssuerEpoch;
    private long combatRestrictedEpoch;
    private long combatForbiddenEpoch;
    @Nullable
    private ResourceLocation combatDimension;
    @Nullable
    private UUID behaviorIssuerUuid;
    @Nullable
    private UUID behaviorLeaseId;
    private long behaviorIssuerEpoch;
    private long behaviorIssuerDeathEpoch;
    @Nullable
    private ResourceLocation behaviorDimension;
    @Nullable
    private MobBehaviorMode behaviorMode;

    public boolean active() {
        return combatActive() || behaviorActive();
    }

    public boolean belongsTo(UUID subject) {
        return subject != null && subject.equals(subjectUuid);
    }

    public boolean combatActive() {
        return subjectUuid != null
                && combatIssuerUuid != null
                && combatForbiddenUuid != null
                && combatLeaseId != null
                && combatDimension != null;
    }

    public boolean behaviorActive() {
        return subjectUuid != null
                && behaviorIssuerUuid != null
                && behaviorLeaseId != null
                && behaviorDimension != null
                && behaviorMode != null;
    }

    @Nullable
    public UUID combatIssuerUuid() {
        return combatIssuerUuid;
    }

    @Nullable
    public UUID combatForbiddenUuid() {
        return combatForbiddenUuid;
    }

    @Nullable
    public UUID combatLeaseId() {
        return combatLeaseId;
    }

    public long combatIssuerEpoch() {
        return combatIssuerEpoch;
    }

    public long combatRestrictedEpoch() {
        return combatRestrictedEpoch;
    }

    public void setCombatRestrictedEpoch(long epoch) {
        combatRestrictedEpoch = epoch;
    }

    public long combatForbiddenEpoch() {
        return combatForbiddenEpoch;
    }

    @Nullable
    public ResourceLocation combatDimension() {
        return combatDimension;
    }

    @Nullable
    public UUID behaviorIssuerUuid() {
        return behaviorIssuerUuid;
    }

    @Nullable
    public UUID behaviorLeaseId() {
        return behaviorLeaseId;
    }

    public long behaviorIssuerEpoch() {
        return behaviorIssuerEpoch;
    }

    public long behaviorIssuerDeathEpoch() {
        return behaviorIssuerDeathEpoch;
    }

    @Nullable
    public ResourceLocation behaviorDimension() {
        return behaviorDimension;
    }

    @Nullable
    public MobBehaviorMode behaviorMode() {
        return behaviorMode;
    }

    public void markCombat(
            UUID subject,
            UUID issuer,
            UUID forbidden,
            UUID leaseId,
            long issuerEpoch,
            long forbiddenEpoch,
            ResourceLocation dimension) {
        subjectUuid = subject;
        combatIssuerUuid = issuer;
        combatForbiddenUuid = forbidden;
        combatLeaseId = leaseId;
        combatIssuerEpoch = issuerEpoch;
        combatForbiddenEpoch = forbiddenEpoch;
        combatDimension = dimension;
    }

    public void markBehavior(
            UUID subject,
            UUID issuer,
            UUID leaseId,
            long issuerEpoch,
            long issuerDeathEpoch,
            ResourceLocation dimension,
            MobBehaviorMode mode) {
        subjectUuid = subject;
        behaviorIssuerUuid = issuer;
        behaviorLeaseId = leaseId;
        behaviorIssuerEpoch = issuerEpoch;
        behaviorIssuerDeathEpoch = issuerDeathEpoch;
        behaviorDimension = dimension;
        behaviorMode = mode;
    }

    public void clearCombat() {
        combatIssuerUuid = null;
        combatForbiddenUuid = null;
        combatLeaseId = null;
        combatIssuerEpoch = 0L;
        combatRestrictedEpoch = 0L;
        combatForbiddenEpoch = 0L;
        combatDimension = null;
        clearSubjectIfUnused();
    }

    public void clearBehavior() {
        behaviorIssuerUuid = null;
        behaviorLeaseId = null;
        behaviorIssuerEpoch = 0L;
        behaviorIssuerDeathEpoch = 0L;
        behaviorDimension = null;
        behaviorMode = null;
        clearSubjectIfUnused();
    }

    public void clear() {
        clearCombat();
        clearBehavior();
        subjectUuid = null;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        if (!active()) {
            return tag;
        }
        tag.putUUID(SUBJECT_TAG, subjectUuid);
        if (combatActive()) {
            CompoundTag combat = new CompoundTag();
            combat.putUUID(ISSUER_TAG, combatIssuerUuid);
            combat.putUUID(FORBIDDEN_TAG, combatForbiddenUuid);
            combat.putUUID(LEASE_TAG, combatLeaseId);
            combat.putLong(
                    ISSUER_EPOCH_TAG, combatIssuerEpoch);
            combat.putLong(RESTRICTED_EPOCH_TAG, combatRestrictedEpoch);
            combat.putLong(
                    FORBIDDEN_EPOCH_TAG,
                    combatForbiddenEpoch);
            combat.putString(
                    DIMENSION_TAG, combatDimension.toString());
            tag.put(COMBAT_TAG, combat);
        }
        if (behaviorActive()) {
            CompoundTag behavior = new CompoundTag();
            behavior.putUUID(ISSUER_TAG, behaviorIssuerUuid);
            behavior.putUUID(LEASE_TAG, behaviorLeaseId);
            behavior.putLong(
                    ISSUER_EPOCH_TAG, behaviorIssuerEpoch);
            behavior.putLong(ISSUER_DEATH_EPOCH_TAG, behaviorIssuerDeathEpoch);
            behavior.putString(
                    DIMENSION_TAG, behaviorDimension.toString());
            behavior.putString(MODE_TAG, behaviorMode.name());
            tag.put(BEHAVIOR_TAG, behavior);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt(VERSION_TAG) < 1 || tag.getInt(VERSION_TAG) > SERIALIZATION_VERSION
                || !tag.hasUUID(SUBJECT_TAG)) {
            return;
        }
        subjectUuid = tag.getUUID(SUBJECT_TAG);
        readCombat(tag.getCompound(COMBAT_TAG));
        readBehavior(tag.getCompound(BEHAVIOR_TAG));
        clearSubjectIfUnused();
    }

    private void readCombat(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(DIMENSION_TAG));
        if (!tag.hasUUID(ISSUER_TAG)
                || !tag.hasUUID(FORBIDDEN_TAG)
                || !tag.hasUUID(LEASE_TAG)
                || dimension == null) {
            return;
        }
        combatIssuerUuid = tag.getUUID(ISSUER_TAG);
        combatForbiddenUuid = tag.getUUID(FORBIDDEN_TAG);
        combatLeaseId = tag.getUUID(LEASE_TAG);
        combatIssuerEpoch = tag.getLong(ISSUER_EPOCH_TAG);
        combatRestrictedEpoch = Math.max(0L, tag.getLong(RESTRICTED_EPOCH_TAG));
        combatForbiddenEpoch =
                tag.getLong(FORBIDDEN_EPOCH_TAG);
        combatDimension = dimension;
    }

    private void readBehavior(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(DIMENSION_TAG));
        MobBehaviorMode mode;
        try {
            mode = MobBehaviorMode.valueOf(
                    tag.getString(MODE_TAG));
        }
        catch (IllegalArgumentException exception) {
            return;
        }
        if (!tag.hasUUID(ISSUER_TAG)
                || !tag.hasUUID(LEASE_TAG)
                || dimension == null
                || mode != MobBehaviorMode.PEACEFUL
                        && mode
                                != MobBehaviorMode
                                        .AGGRESSIVE_NEAREST_8) {
            return;
        }
        behaviorIssuerUuid = tag.getUUID(ISSUER_TAG);
        behaviorLeaseId = tag.getUUID(LEASE_TAG);
        behaviorIssuerEpoch = tag.getLong(ISSUER_EPOCH_TAG);
        // Old records can match only the original world-saved life, never a newer death generation.
        behaviorIssuerDeathEpoch = Math.max(0L, tag.getLong(ISSUER_DEATH_EPOCH_TAG));
        behaviorDimension = dimension;
        behaviorMode = mode;
    }

    private void clearSubjectIfUnused() {
        if (combatIssuerUuid == null
                && behaviorIssuerUuid == null) {
            subjectUuid = null;
        }
    }
}

package rotp.addons.pearljam.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.pearljam.food.PearlJamTreatment;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class PearlJamPlayerState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 2;

    private static final String VERSION_TAG = "Version";
    private static final String TREATMENT_TAG = "Treatment";
    private static final String TARGET_TAG = "Target";
    private static final String DIAGNOSIS_EXPIRES_TAG =
            "DiagnosisExpires";
    private static final String TREATMENT_LOCK_TAG =
            "TreatmentLock";
    private static final String MAIN_HAND_TAG = "MainHand";
    private static final String HIDE_CHEF_TAG = "HideChef";
    private static final String CRAFT_PASSIVE_TAG =
            "CraftPassive";

    public static final StreamCodec<
            RegistryFriendlyByteBuf, PearlJamPlayerState> STREAM_CODEC =
            StreamCodec.ofMember(
                    PearlJamPlayerState::encode,
                    PearlJamPlayerState::decode);

    private PearlJamTreatment treatment = PearlJamTreatment.NONE;
    @Nullable
    private UUID targetUuid;
    private long diagnosisExpiresAt;
    private long treatmentLockedUntil;
    private boolean mainHandSelected;
    private boolean chefSignatureHidden;
    private boolean craftPassiveEnabled;

    public PearlJamTreatment treatment() {
        return treatment;
    }

    @Nullable
    public UUID targetUuid() {
        return targetUuid;
    }

    public long diagnosisExpiresAt() {
        return diagnosisExpiresAt;
    }

    public long treatmentLockedUntil() {
        return treatmentLockedUntil;
    }

    public boolean mainHandSelected() {
        return mainHandSelected;
    }

    public boolean chefSignatureHidden() {
        return chefSignatureHidden;
    }

    public boolean craftPassiveEnabled() {
        return craftPassiveEnabled;
    }

    public boolean hasUsableDiagnosis(long gameTime) {
        return treatment.isActive()
                && targetUuid != null
                && diagnosisExpiresAt > gameTime;
    }

    public boolean treatmentLocked(long gameTime) {
        return treatmentLockedUntil > gameTime;
    }

    public boolean diagnose(
            PearlJamTreatment newTreatment,
            UUID newTargetUuid,
            long newExpiresAt) {
        if (newTreatment == null
                || !newTreatment.isActive()
                || newTargetUuid == null) {
            return clearDiagnosis();
        }
        long expiresAt = Math.max(0L, newExpiresAt);
        boolean changed = treatment != newTreatment
                || !newTargetUuid.equals(targetUuid)
                || diagnosisExpiresAt != expiresAt;
        treatment = newTreatment;
        targetUuid = newTargetUuid;
        diagnosisExpiresAt = expiresAt;
        return changed;
    }

    public boolean clearDiagnosis() {
        boolean changed = treatment.isActive()
                || targetUuid != null
                || diagnosisExpiresAt != 0L;
        treatment = PearlJamTreatment.NONE;
        targetUuid = null;
        diagnosisExpiresAt = 0L;
        return changed;
    }

    public boolean lockTreatmentUntil(long gameTime) {
        long lock = Math.max(0L, gameTime);
        if (treatmentLockedUntil == lock) {
            return false;
        }
        treatmentLockedUntil = lock;
        return true;
    }

    public boolean toggleMainHand() {
        mainHandSelected = !mainHandSelected;
        return mainHandSelected;
    }

    public boolean toggleChefSignature() {
        chefSignatureHidden = !chefSignatureHidden;
        return chefSignatureHidden;
    }

    public boolean toggleCraftPassive() {
        craftPassiveEnabled = !craftPassiveEnabled;
        return craftPassiveEnabled;
    }

    public boolean clearStandState() {
        boolean changed = clearDiagnosis()
                | mainHandSelected
                | chefSignatureHidden
                | craftPassiveEnabled;
        mainHandSelected = false;
        chefSignatureHidden = false;
        craftPassiveEnabled = false;
        return changed;
    }

    public boolean expire(long gameTime) {
        boolean changed = false;
        if (diagnosisExpiresAt > 0L
                && diagnosisExpiresAt <= gameTime) {
            changed |= clearDiagnosis();
        }
        if (treatmentLockedUntil > 0L
                && treatmentLockedUntil <= gameTime) {
            treatmentLockedUntil = 0L;
            changed = true;
        }
        return changed;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putString(TREATMENT_TAG, treatment.serializedName());
        if (targetUuid != null) {
            tag.putUUID(TARGET_TAG, targetUuid);
        }
        tag.putLong(DIAGNOSIS_EXPIRES_TAG, diagnosisExpiresAt);
        tag.putLong(TREATMENT_LOCK_TAG, treatmentLockedUntil);
        tag.putBoolean(MAIN_HAND_TAG, mainHandSelected);
        tag.putBoolean(HIDE_CHEF_TAG, chefSignatureHidden);
        tag.putBoolean(
                CRAFT_PASSIVE_TAG, craftPassiveEnabled);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clearDiagnosis();
        treatmentLockedUntil = 0L;
        mainHandSelected = false;
        chefSignatureHidden = false;
        craftPassiveEnabled = false;
        int version = tag.getInt(VERSION_TAG);
        if (version != 1
                && version != SERIALIZATION_VERSION) {
            return;
        }
        PearlJamTreatment loaded =
                PearlJamTreatment.bySerializedName(
                        tag.getString(TREATMENT_TAG));
        if (loaded.isActive() && tag.hasUUID(TARGET_TAG)) {
            treatment = loaded;
            targetUuid = tag.getUUID(TARGET_TAG);
            diagnosisExpiresAt = Math.max(
                    0L, tag.getLong(DIAGNOSIS_EXPIRES_TAG));
        }
        treatmentLockedUntil = Math.max(
                0L, tag.getLong(TREATMENT_LOCK_TAG));
        if (version >= 2) {
            mainHandSelected = tag.getBoolean(MAIN_HAND_TAG);
            chefSignatureHidden =
                    tag.getBoolean(HIDE_CHEF_TAG);
            craftPassiveEnabled =
                    tag.getBoolean(CRAFT_PASSIVE_TAG);
        }
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(treatment.ordinal());
        buffer.writeBoolean(targetUuid != null);
        if (targetUuid != null) {
            buffer.writeUUID(targetUuid);
        }
        buffer.writeVarLong(diagnosisExpiresAt);
        buffer.writeVarLong(treatmentLockedUntil);
        buffer.writeBoolean(mainHandSelected);
        buffer.writeBoolean(chefSignatureHidden);
        buffer.writeBoolean(craftPassiveEnabled);
    }

    private static PearlJamPlayerState decode(
            RegistryFriendlyByteBuf buffer) {
        PearlJamPlayerState state = new PearlJamPlayerState();
        PearlJamTreatment treatment =
                PearlJamTreatment.byId(buffer.readVarInt());
        UUID target = buffer.readBoolean()
                ? buffer.readUUID()
                : null;
        long expiresAt = Math.max(0L, buffer.readVarLong());
        long treatmentLock = Math.max(0L, buffer.readVarLong());
        if (treatment.isActive() && target != null) {
            state.diagnose(treatment, target, expiresAt);
        }
        state.lockTreatmentUntil(treatmentLock);
        state.mainHandSelected = buffer.readBoolean();
        state.chefSignatureHidden = buffer.readBoolean();
        state.craftPassiveEnabled = buffer.readBoolean();
        return state;
    }
}

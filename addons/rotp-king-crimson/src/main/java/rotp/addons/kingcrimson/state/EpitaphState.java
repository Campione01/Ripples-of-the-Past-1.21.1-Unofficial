package rotp.addons.kingcrimson.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EpitaphState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String STAGE_TAG = "Stage";
    private static final String PREDICTION_TAG = "Prediction";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String ATTACKER_TAG = "PredictedAttacker";
    private static final String DAMAGE_TYPE_TAG = "PredictedDamageType";

    private Stage stage = Stage.IDLE;
    private PredictionKind predictionKind = PredictionKind.NONE;
    private long expiresAt;
    @Nullable
    private UUID predictedAttackerUuid;
    @Nullable
    private ResourceLocation predictedDamageType;
    /** Server only and never saved: the creatures bound to what this Epitaph foresaw, by creature UUID. */
    private final Map<UUID, EpitaphFate> fates = new HashMap<>();
    /** Server only and never saved: creatures just released from a fate, until the game time they are foreseen again. */
    private final Map<UUID, Long> redeciding = new HashMap<>();

    public Stage stage() {
        return stage;
    }

    public PredictionKind predictionKind() {
        return predictionKind;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public Map<UUID, EpitaphFate> fates() {
        return fates;
    }

    public Map<UUID, Long> redeciding() {
        return redeciding;
    }

    @Nullable
    public UUID predictedAttackerUuid() {
        return predictedAttackerUuid;
    }

    @Nullable
    public ResourceLocation predictedDamageType() {
        return predictedDamageType;
    }

    public boolean isArmed(long gameTime) {
        return stage == Stage.ARMED && gameTime < expiresAt;
    }

    public boolean isPresentationActive(long gameTime) {
        return stage != Stage.IDLE && gameTime < expiresAt;
    }

    public boolean arm(
            PredictionKind prediction, long newExpiresAt) {
        if (prediction == null
                || prediction == PredictionKind.NONE
                || newExpiresAt <= 0L) {
            return clear();
        }
        boolean changed = stage != Stage.ARMED
                || predictionKind != prediction
                || expiresAt != newExpiresAt
                || predictedAttackerUuid != null
                || predictedDamageType != null;
        stage = Stage.ARMED;
        predictionKind = prediction;
        expiresAt = newExpiresAt;
        predictedAttackerUuid = null;
        predictedDamageType = null;
        return changed;
    }

    public boolean recordDodge(
            @Nullable UUID attackerUuid,
            @Nullable ResourceLocation damageType,
            long feedbackExpiresAt) {
        if (stage != Stage.ARMED) {
            return false;
        }
        stage = Stage.DODGED;
        expiresAt = Math.max(feedbackExpiresAt, 1L);
        predictedAttackerUuid = attackerUuid;
        predictedDamageType = damageType;
        return true;
    }

    public boolean clear() {
        boolean changed = stage != Stage.IDLE
                || predictionKind != PredictionKind.NONE
                || expiresAt != 0L
                || predictedAttackerUuid != null
                || predictedDamageType != null;
        stage = Stage.IDLE;
        predictionKind = PredictionKind.NONE;
        expiresAt = 0L;
        predictedAttackerUuid = null;
        predictedDamageType = null;
        return changed;
    }

    public void applyClientSnapshot(
            int stageOrdinal,
            int predictionOrdinal,
            long newExpiresAt,
            @Nullable UUID attackerUuid,
            @Nullable ResourceLocation damageType) {
        Stage newStage = Stage.byOrdinal(stageOrdinal);
        PredictionKind newPrediction =
                PredictionKind.byOrdinal(predictionOrdinal);
        clear();
        if (newStage == Stage.IDLE
                || newPrediction == PredictionKind.NONE
                || newExpiresAt <= 0L) {
            return;
        }
        stage = newStage;
        predictionKind = newPrediction;
        expiresAt = newExpiresAt;
        predictedAttackerUuid = attackerUuid;
        predictedDamageType = damageType;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putInt(STAGE_TAG, stage.ordinal());
        tag.putInt(PREDICTION_TAG, predictionKind.ordinal());
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        if (predictedAttackerUuid != null) {
            tag.putUUID(ATTACKER_TAG, predictedAttackerUuid);
        }
        if (predictedDamageType != null) {
            tag.putString(
                    DAMAGE_TYPE_TAG,
                    predictedDamageType.toString());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt(VERSION_TAG) != SERIALIZATION_VERSION) {
            return;
        }
        Stage loadedStage = Stage.byOrdinal(
                tag.getInt(STAGE_TAG));
        PredictionKind loadedPrediction =
                PredictionKind.byOrdinal(
                        tag.getInt(PREDICTION_TAG));
        long loadedExpiresAt = tag.getLong(EXPIRES_AT_TAG);
        if (loadedStage == Stage.IDLE
                || loadedPrediction == PredictionKind.NONE
                || loadedExpiresAt <= 0L) {
            return;
        }
        stage = loadedStage;
        predictionKind = loadedPrediction;
        expiresAt = loadedExpiresAt;
        if (tag.hasUUID(ATTACKER_TAG)) {
            predictedAttackerUuid = tag.getUUID(ATTACKER_TAG);
        }
        predictedDamageType = ResourceLocation.tryParse(
                tag.getString(DAMAGE_TYPE_TAG));
    }

    public enum Stage {
        IDLE,
        ARMED,
        DODGED;

        public static Stage byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal]
                    : IDLE;
        }
    }

    public enum PredictionKind {
        NONE,
        DIRECT_ATTACK,
        ANY_NON_BYPASSING;

        public static PredictionKind byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal]
                    : NONE;
        }
    }
}

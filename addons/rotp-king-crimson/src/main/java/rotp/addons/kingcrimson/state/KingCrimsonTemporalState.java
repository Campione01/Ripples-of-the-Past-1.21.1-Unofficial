package rotp.addons.kingcrimson.state;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class KingCrimsonTemporalState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String MODE_TAG = "Mode";
    private static final String STARTED_AT_TAG = "StartedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String PREVIOUS_INVISIBILITY_TAG =
            "PreviousInvisibility";
    private static final String PREVIOUS_INFINITE_TAG =
            "PreviousInfinite";
    private static final String PREVIOUS_DURATION_TAG =
            "PreviousDuration";
    private static final String PREVIOUS_AMPLIFIER_TAG =
            "PreviousAmplifier";
    private static final String PREVIOUS_AMBIENT_TAG =
            "PreviousAmbient";
    private static final String PREVIOUS_VISIBLE_TAG =
            "PreviousVisible";
    private static final String PREVIOUS_SHOW_ICON_TAG =
            "PreviousShowIcon";
    private static final String PREVIOUS_FULL_INVISIBILITY_TAG =
            "PreviousFullInvisibility";
    private static final String HIDDEN_STAND_UUID_TAG =
            "HiddenStandUuid";
    private static final String PREVIOUS_STAND_INVISIBILITY_TAG =
            "PreviousStandInvisibility";

    private Mode mode = Mode.IDLE;
    private long startedAt;
    private long expiresAt;
    private boolean hadPreviousInvisibility;
    private boolean previousInvisibilityInfinite;
    private int previousInvisibilityDuration;
    private int previousInvisibilityAmplifier;
    private boolean previousInvisibilityAmbient;
    private boolean previousInvisibilityVisible;
    private boolean previousInvisibilityShowIcon;
    @Nullable
    private EffectSnapshot previousFullInvisibility;
    @Nullable
    private UUID hiddenStandUuid;
    @Nullable
    private EffectSnapshot previousStandInvisibility;
    @Nullable
    private transient KingCrimsonTimeEraseProjectionService.Session
            projectionSession;

    public Mode mode() {
        return mode;
    }

    public long startedAt() {
        return startedAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isIdle() {
        return mode == Mode.IDLE;
    }

    public boolean isErasing(long gameTime) {
        return mode == Mode.TIME_ERASE && gameTime < expiresAt;
    }

    public boolean isSkipping(long gameTime) {
        return mode == Mode.TIME_SKIP && gameTime < expiresAt;
    }

    public boolean beginTimeErase(
            long newStartedAt,
            long newExpiresAt,
            @Nullable MobEffectInstance previousInvisibility,
            @Nullable MobEffectInstance previousFullInvisibility,
            @Nullable UUID hiddenStandUuid,
            @Nullable MobEffectInstance previousStandInvisibility) {
        if (!isIdle()
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return false;
        }
        mode = Mode.TIME_ERASE;
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
        rememberPreviousInvisibility(previousInvisibility);
        this.previousFullInvisibility =
                EffectSnapshot.from(previousFullInvisibility);
        this.hiddenStandUuid = hiddenStandUuid;
        this.previousStandInvisibility =
                EffectSnapshot.from(previousStandInvisibility);
        return true;
    }

    public boolean beginTimeSkip(
            long newStartedAt, long newExpiresAt) {
        if (!isIdle()
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return false;
        }
        mode = Mode.TIME_SKIP;
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
        clearPreviousInvisibility();
        clearVisibilitySnapshots();
        return true;
    }

    public int elapsedTicks(long gameTime, int maximum) {
        long elapsed = Math.max(0L, gameTime - startedAt);
        return (int) Math.min(elapsed, Math.max(maximum, 0));
    }

    @Nullable
    public MobEffectInstance previousInvisibility(
            long gameTime) {
        if (!hadPreviousInvisibility) {
            return null;
        }
        int duration;
        if (previousInvisibilityInfinite) {
            duration = MobEffectInstance.INFINITE_DURATION;
        } else {
            long elapsed = Math.max(0L, gameTime - startedAt);
            duration = (int) Math.max(
                    0L,
                    (long) previousInvisibilityDuration - elapsed);
            if (duration <= 0) {
                return null;
            }
        }
        return new MobEffectInstance(
                net.minecraft.world.effect.MobEffects.INVISIBILITY,
                duration,
                previousInvisibilityAmplifier,
                previousInvisibilityAmbient,
                previousInvisibilityVisible,
                previousInvisibilityShowIcon);
    }

    @Nullable
    public MobEffectInstance previousFullInvisibility(
            long gameTime) {
        return restoreEffect(
                previousFullInvisibility,
                ModStatusEffects.FULL_INVISIBILITY,
                gameTime);
    }

    @Nullable
    public UUID hiddenStandUuid() {
        return hiddenStandUuid;
    }

    @Nullable
    public MobEffectInstance previousStandInvisibility(
            long gameTime) {
        return restoreEffect(
                previousStandInvisibility,
                MobEffects.INVISIBILITY,
                gameTime);
    }

    public boolean clear() {
        boolean changed = mode != Mode.IDLE
                || startedAt != 0L
                || expiresAt != 0L
                || hadPreviousInvisibility
                || previousFullInvisibility != null
                || hiddenStandUuid != null
                || previousStandInvisibility != null;
        mode = Mode.IDLE;
        startedAt = 0L;
        expiresAt = 0L;
        clearPreviousInvisibility();
        clearVisibilitySnapshots();
        projectionSession = null;
        return changed;
    }

    @Nullable
    KingCrimsonTimeEraseProjectionService.Session
            projectionSession() {
        return projectionSession;
    }

    void setProjectionSession(
            KingCrimsonTimeEraseProjectionService.Session session) {
        projectionSession = session;
    }

    @Nullable
    KingCrimsonTimeEraseProjectionService.Session
            takeProjectionSession() {
        KingCrimsonTimeEraseProjectionService.Session session =
                projectionSession;
        projectionSession = null;
        return session;
    }

    public void applyClientSnapshot(
            int modeOrdinal,
            long newStartedAt,
            long newExpiresAt) {
        clear();
        Mode newMode = Mode.byOrdinal(modeOrdinal);
        if (newMode == Mode.IDLE
                || newStartedAt < 0L
                || newExpiresAt <= newStartedAt) {
            return;
        }
        mode = newMode;
        startedAt = newStartedAt;
        expiresAt = newExpiresAt;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putInt(MODE_TAG, mode.ordinal());
        tag.putLong(STARTED_AT_TAG, startedAt);
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        tag.putBoolean(
                PREVIOUS_INVISIBILITY_TAG,
                hadPreviousInvisibility);
        if (hadPreviousInvisibility) {
            tag.putBoolean(
                    PREVIOUS_INFINITE_TAG,
                    previousInvisibilityInfinite);
            tag.putInt(
                    PREVIOUS_DURATION_TAG,
                    previousInvisibilityDuration);
            tag.putInt(
                    PREVIOUS_AMPLIFIER_TAG,
                    previousInvisibilityAmplifier);
            tag.putBoolean(
                    PREVIOUS_AMBIENT_TAG,
                    previousInvisibilityAmbient);
            tag.putBoolean(
                    PREVIOUS_VISIBLE_TAG,
                    previousInvisibilityVisible);
            tag.putBoolean(
                    PREVIOUS_SHOW_ICON_TAG,
                    previousInvisibilityShowIcon);
        }
        if (previousFullInvisibility != null) {
            tag.put(
                    PREVIOUS_FULL_INVISIBILITY_TAG,
                    previousFullInvisibility.serialize());
        }
        if (hiddenStandUuid != null) {
            tag.putUUID(HIDDEN_STAND_UUID_TAG, hiddenStandUuid);
        }
        if (previousStandInvisibility != null) {
            tag.put(
                    PREVIOUS_STAND_INVISIBILITY_TAG,
                    previousStandInvisibility.serialize());
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
        Mode loadedMode = Mode.byOrdinal(tag.getInt(MODE_TAG));
        long loadedStartedAt = tag.getLong(STARTED_AT_TAG);
        long loadedExpiresAt = tag.getLong(EXPIRES_AT_TAG);
        if (loadedMode == Mode.IDLE
                || loadedStartedAt < 0L
                || loadedExpiresAt <= loadedStartedAt) {
            return;
        }
        mode = loadedMode;
        startedAt = loadedStartedAt;
        expiresAt = loadedExpiresAt;
        if (loadedMode == Mode.TIME_ERASE
                && tag.getBoolean(PREVIOUS_INVISIBILITY_TAG)) {
            hadPreviousInvisibility = true;
            previousInvisibilityInfinite =
                    tag.getBoolean(PREVIOUS_INFINITE_TAG);
            previousInvisibilityDuration =
                    tag.getInt(PREVIOUS_DURATION_TAG);
            previousInvisibilityAmplifier =
                    tag.getInt(PREVIOUS_AMPLIFIER_TAG);
            previousInvisibilityAmbient =
                    tag.getBoolean(PREVIOUS_AMBIENT_TAG);
            previousInvisibilityVisible =
                    tag.getBoolean(PREVIOUS_VISIBLE_TAG);
            previousInvisibilityShowIcon =
                    tag.getBoolean(PREVIOUS_SHOW_ICON_TAG);
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.contains(PREVIOUS_FULL_INVISIBILITY_TAG)) {
            previousFullInvisibility = EffectSnapshot.deserialize(
                    tag.getCompound(
                            PREVIOUS_FULL_INVISIBILITY_TAG));
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.hasUUID(HIDDEN_STAND_UUID_TAG)) {
            hiddenStandUuid = tag.getUUID(HIDDEN_STAND_UUID_TAG);
        }
        if (loadedMode == Mode.TIME_ERASE
                && tag.contains(PREVIOUS_STAND_INVISIBILITY_TAG)) {
            previousStandInvisibility = EffectSnapshot.deserialize(
                    tag.getCompound(
                            PREVIOUS_STAND_INVISIBILITY_TAG));
        }
    }

    private void rememberPreviousInvisibility(
            @Nullable MobEffectInstance previous) {
        clearPreviousInvisibility();
        if (previous == null) {
            return;
        }
        hadPreviousInvisibility = true;
        previousInvisibilityInfinite =
                previous.isInfiniteDuration();
        previousInvisibilityDuration = previous.getDuration();
        previousInvisibilityAmplifier = previous.getAmplifier();
        previousInvisibilityAmbient = previous.isAmbient();
        previousInvisibilityVisible = previous.isVisible();
        previousInvisibilityShowIcon = previous.showIcon();
    }

    private void clearPreviousInvisibility() {
        hadPreviousInvisibility = false;
        previousInvisibilityInfinite = false;
        previousInvisibilityDuration = 0;
        previousInvisibilityAmplifier = 0;
        previousInvisibilityAmbient = false;
        previousInvisibilityVisible = false;
        previousInvisibilityShowIcon = false;
    }

    private void clearVisibilitySnapshots() {
        previousFullInvisibility = null;
        hiddenStandUuid = null;
        previousStandInvisibility = null;
    }

    @Nullable
    private MobEffectInstance restoreEffect(
            @Nullable EffectSnapshot snapshot,
            Holder<MobEffect> effect,
            long gameTime) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.restore(
                effect,
                Math.max(0L, gameTime - startedAt));
    }

    private record EffectSnapshot(
            boolean infinite,
            int duration,
            int amplifier,
            boolean ambient,
            boolean visible,
            boolean showIcon) {
        private static final String INFINITE_TAG = "Infinite";
        private static final String DURATION_TAG = "Duration";
        private static final String AMPLIFIER_TAG = "Amplifier";
        private static final String AMBIENT_TAG = "Ambient";
        private static final String VISIBLE_TAG = "Visible";
        private static final String SHOW_ICON_TAG = "ShowIcon";

        @Nullable
        static EffectSnapshot from(
                @Nullable MobEffectInstance effect) {
            return effect == null
                    ? null
                    : new EffectSnapshot(
                            effect.isInfiniteDuration(),
                            effect.getDuration(),
                            effect.getAmplifier(),
                            effect.isAmbient(),
                            effect.isVisible(),
                            effect.showIcon());
        }

        CompoundTag serialize() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(INFINITE_TAG, infinite);
            tag.putInt(DURATION_TAG, duration);
            tag.putInt(AMPLIFIER_TAG, amplifier);
            tag.putBoolean(AMBIENT_TAG, ambient);
            tag.putBoolean(VISIBLE_TAG, visible);
            tag.putBoolean(SHOW_ICON_TAG, showIcon);
            return tag;
        }

        static EffectSnapshot deserialize(CompoundTag tag) {
            return new EffectSnapshot(
                    tag.getBoolean(INFINITE_TAG),
                    tag.getInt(DURATION_TAG),
                    tag.getInt(AMPLIFIER_TAG),
                    tag.getBoolean(AMBIENT_TAG),
                    tag.getBoolean(VISIBLE_TAG),
                    tag.getBoolean(SHOW_ICON_TAG));
        }

        @Nullable
        MobEffectInstance restore(
                Holder<MobEffect> effect, long elapsed) {
            int remaining;
            if (infinite) {
                remaining = MobEffectInstance.INFINITE_DURATION;
            } else {
                remaining = (int) Math.max(
                        0L, (long) duration - elapsed);
                if (remaining <= 0) {
                    return null;
                }
            }
            return new MobEffectInstance(
                    effect,
                    remaining,
                    amplifier,
                    ambient,
                    visible,
                    showIcon);
        }
    }

    public enum Mode {
        IDLE,
        TIME_ERASE,
        TIME_SKIP;

        public static Mode byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length
                    ? values()[ordinal]
                    : IDLE;
        }
    }
}

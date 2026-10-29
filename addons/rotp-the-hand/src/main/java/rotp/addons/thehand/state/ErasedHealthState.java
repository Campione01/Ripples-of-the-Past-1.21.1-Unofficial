package rotp.addons.thehand.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ErasedHealthState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 1;

    private static final String VERSION_TAG = "Version";
    private static final String ERASED_HEALTH_TAG = "ErasedHealth";
    private static final String EXPIRES_AT_TAG = "ExpiresAtGameTime";
    private static final String SOURCE_USER_TAG = "SourceUser";
    private static final String SOURCE_DIMENSION_TAG = "SourceDimension";
    private static final String TARGET_DIMENSION_TAG = "TargetDimension";

    private float erasedHealth;
    private long expiresAtGameTime;
    @Nullable
    private UUID sourceUser;
    @Nullable
    private ResourceLocation sourceDimension;
    @Nullable
    private ResourceLocation targetDimension;

    public float erasedHealth() {
        return erasedHealth;
    }

    public long expiresAtGameTime() {
        return expiresAtGameTime;
    }

    @Nullable
    public UUID sourceUser() {
        return sourceUser;
    }

    @Nullable
    public ResourceLocation sourceDimension() {
        return sourceDimension;
    }

    public boolean activeAt(long gameTime) {
        return erasedHealth > 0.0F
                && expiresAtGameTime > gameTime
                && sourceUser != null
                && sourceDimension != null
                && targetDimension != null;
    }

    public boolean isOwnedBy(UUID ownerUuid) {
        return ownerUuid != null && ownerUuid.equals(sourceUser);
    }

    public boolean isInTargetDimension(
            ResourceLocation dimension) {
        return dimension != null && dimension.equals(targetDimension);
    }

    public boolean applyServer(
            float newErasedHealth,
            long newExpiresAtGameTime,
            UUID newSourceUser,
            ResourceLocation newSourceDimension,
            ResourceLocation newTargetDimension) {
        float normalizedHealth = normalize(newErasedHealth);
        if (normalizedHealth <= 0.0F
                || newExpiresAtGameTime <= 0L
                || newSourceUser == null
                || newSourceDimension == null
                || newTargetDimension == null) {
            return clear();
        }
        boolean changed = Float.compare(
                        erasedHealth, normalizedHealth) != 0
                || expiresAtGameTime != newExpiresAtGameTime
                || !newSourceUser.equals(sourceUser)
                || !newSourceDimension.equals(sourceDimension)
                || !newTargetDimension.equals(targetDimension);
        erasedHealth = normalizedHealth;
        expiresAtGameTime = newExpiresAtGameTime;
        sourceUser = newSourceUser;
        sourceDimension = newSourceDimension;
        targetDimension = newTargetDimension;
        return changed;
    }

    public void applyClientSnapshot(
            float newErasedHealth, long newExpiresAtGameTime) {
        erasedHealth = normalize(newErasedHealth);
        expiresAtGameTime = erasedHealth > 0.0F
                ? Math.max(newExpiresAtGameTime, 0L)
                : 0L;
        sourceUser = null;
        sourceDimension = null;
        targetDimension = null;
    }

    public boolean clear() {
        boolean changed = erasedHealth != 0.0F
                || expiresAtGameTime != 0L
                || sourceUser != null
                || sourceDimension != null
                || targetDimension != null;
        erasedHealth = 0.0F;
        expiresAtGameTime = 0L;
        sourceUser = null;
        sourceDimension = null;
        targetDimension = null;
        return changed;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (erasedHealth <= 0.0F
                || sourceUser == null
                || sourceDimension == null
                || targetDimension == null) {
            return tag;
        }
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putFloat(ERASED_HEALTH_TAG, erasedHealth);
        tag.putLong(EXPIRES_AT_TAG, expiresAtGameTime);
        tag.putUUID(SOURCE_USER_TAG, sourceUser);
        tag.putString(
                SOURCE_DIMENSION_TAG, sourceDimension.toString());
        tag.putString(
                TARGET_DIMENSION_TAG, targetDimension.toString());
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        if (tag.getInt(VERSION_TAG) != SERIALIZATION_VERSION
                || !tag.contains(ERASED_HEALTH_TAG)
                || !tag.contains(EXPIRES_AT_TAG)
                || !tag.hasUUID(SOURCE_USER_TAG)
                || !tag.contains(SOURCE_DIMENSION_TAG)
                || !tag.contains(TARGET_DIMENSION_TAG)) {
            return;
        }
        try {
            applyServer(
                    tag.getFloat(ERASED_HEALTH_TAG),
                    tag.getLong(EXPIRES_AT_TAG),
                    tag.getUUID(SOURCE_USER_TAG),
                    ResourceLocation.parse(
                            tag.getString(SOURCE_DIMENSION_TAG)),
                    ResourceLocation.parse(
                            tag.getString(TARGET_DIMENSION_TAG)));
        } catch (RuntimeException ignored) {
            clear();
        }
    }

    private static float normalize(float value) {
        return Float.isFinite(value) ? Math.max(value, 0.0F) : 0.0F;
    }
}

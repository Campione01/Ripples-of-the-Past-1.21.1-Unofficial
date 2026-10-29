package rotp.addons.heavensdoor.state;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class BookState
        implements INBTSerializable<CompoundTag> {
    public static final int SERIALIZATION_VERSION = 5;

    private static final String VERSION_TAG = "Version";
    private static final String ACTIVE_TAG = "Active";
    private static final String OWNER_TAG = "Owner";
    private static final String TARGET_TAG = "Target";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String PAGE_COUNT_TAG = "PageCount";
    private static final String GENERATION_TAG = "Generation";
    private static final String BOOK_AMPLIFIER_TAG = "BookAmplifier";
    private static final String RESOLVE_COUNTERED_TAG = "ResolveCountered";
    private static final String BOOK_OWNER_EPOCH_TAG = "BookOwnerEpoch";
    private static final String PAGE_OWNER_TAG = "PageOwner";
    private static final String PAGE_TARGET_TAG = "PageTarget";
    private static final String PAGE_DIMENSION_TAG = "PageDimension";
    private static final String PAGE_GENERATION_TAG = "PageGeneration";
    private static final String PAGE_OWNER_EPOCH_TAG = "PageOwnerEpoch";

    private boolean active;
    @Nullable
    private UUID ownerUuid;
    @Nullable
    private UUID targetUuid;
    @Nullable
    private ResourceLocation dimension;
    private long expiresAt;
    private int pageCount;
    private long generation;
    private int bookAmplifier;
    private boolean resolveCountered;
    private long bookOwnerEpoch;
    @Nullable
    private UUID pageOwnerUuid;
    @Nullable
    private UUID pageTargetUuid;
    @Nullable
    private ResourceLocation pageDimension;
    private long pageGeneration;
    private long pageOwnerEpoch;

    public boolean active() {
        return active
                && ownerUuid != null
                && targetUuid != null
                && dimension != null;
    }

    @Nullable
    public UUID ownerUuid() {
        return ownerUuid;
    }

    @Nullable
    public UUID targetUuid() {
        return targetUuid;
    }

    @Nullable
    public ResourceLocation dimension() {
        return dimension;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public int pageCount() {
        return pageCount;
    }

    public long generation() {
        return generation;
    }

    public int bookAmplifier() {
        return bookAmplifier;
    }

    public long bookOwnerEpoch() {
        return bookOwnerEpoch;
    }

    public boolean hasPages() {
        return pageCount > 0 && pageOwnerUuid != null
                && pageTargetUuid != null && pageDimension != null
                && pageGeneration > 0L;
    }

    @Nullable
    public UUID pageOwnerUuid() {
        return pageOwnerUuid;
    }

    @Nullable
    public UUID pageTargetUuid() {
        return pageTargetUuid;
    }

    @Nullable
    public ResourceLocation pageDimension() {
        return pageDimension;
    }

    public long pageGeneration() {
        return pageGeneration;
    }

    public long pageOwnerEpoch() {
        return pageOwnerEpoch;
    }

    public boolean pagesOwnedBy(UUID owner) {
        return pageCount > 0 && pageOwnerUuid != null && pageOwnerUuid.equals(owner);
    }

    public boolean pagesMatch(
            UUID target, UUID creator,
            ResourceLocation originDimension, long bindingGeneration) {
        return hasPages()
                && pageTargetUuid.equals(target)
                && pageOwnerUuid.equals(creator)
                && pageDimension.equals(originDimension)
                && pageGeneration == bindingGeneration;
    }

    public boolean isOwnedBy(UUID owner) {
        return active()
                && owner != null
                && owner.equals(ownerUuid);
    }

    public boolean matches(
            UUID target, ResourceLocation targetDimension) {
        return active()
                && target != null
                && target.equals(targetUuid)
                && targetDimension != null
                && targetDimension.equals(dimension);
    }

    public boolean mark(
            UUID owner,
            UUID target,
            ResourceLocation targetDimension,
            long newExpiresAt,
            int newBookAmplifier) {
        return mark(owner, target, targetDimension, newExpiresAt, newBookAmplifier, 0L);
    }

    public boolean mark(
            UUID owner, UUID target, ResourceLocation targetDimension,
            long newExpiresAt, int newBookAmplifier, long ownerEpoch) {
        if (owner == null
                || target == null
                || targetDimension == null
                || newExpiresAt <= 0L || ownerEpoch < 0L) {
            return clear();
        }
        boolean sameLease = active()
                && owner.equals(ownerUuid)
                && target.equals(targetUuid)
                && targetDimension.equals(dimension)
                && bookOwnerEpoch == ownerEpoch;
        boolean changed = !sameLease
                || expiresAt != newExpiresAt
                || bookAmplifier != Math.clamp(newBookAmplifier, 0, 2);
        active = true;
        ownerUuid = owner;
        targetUuid = target;
        dimension = targetDimension;
        expiresAt = newExpiresAt;
        bookAmplifier = Math.clamp(newBookAmplifier, 0, 2);
        resolveCountered = false;
        bookOwnerEpoch = ownerEpoch;
        if (!sameLease) {
            generation = generation == Long.MAX_VALUE
                    ? 1L
                    : Math.max(1L, generation + 1L);
        }
        return changed;
    }

    public void markResolveCountered() {
        resolveCountered = true;
    }

    public boolean weakenForResolve(long now) {
        if (!active() || resolveCountered || expiresAt <= now) {
            return false;
        }
        expiresAt = now + (expiresAt - now) / BookStateRules.RESOLVE_COUNTER_DURATION_DIVISOR;
        bookAmplifier = 0;
        resolveCountered = true;
        return true;
    }

    public boolean addPage() {
        return addPage(pageOwnerEpoch);
    }

    public boolean addPage(long ownerEpoch) {
        int next = BookStateRules.clampPages(pageCount + 1);
        if (!active() || next == pageCount || ownerEpoch < 0L
                || hasPages() && (!pagesOwnedBy(ownerUuid)
                        || !pageTargetUuid.equals(targetUuid)
                        || pageOwnerEpoch != ownerEpoch)) {
            return false;
        }
        if (!hasPages()) {
            pageOwnerUuid = ownerUuid;
            pageTargetUuid = targetUuid;
            pageDimension = dimension;
            pageOwnerEpoch = ownerEpoch;
            long previous = Math.max(pageGeneration, generation);
            pageGeneration = previous == Long.MAX_VALUE
                    ? 1L : Math.max(1L, previous + 1L);
        }
        pageCount = next;
        return true;
    }

    public boolean returnPage() {
        int next = BookStateRules.clampPages(pageCount - 1);
        if (!hasPages() || next == pageCount) {
            return false;
        }
        pageCount = next;
        if (pageCount == 0) {
            clearPages();
        }
        return true;
    }

    public boolean clearPages() {
        boolean changed = pageCount != 0 || pageOwnerUuid != null;
        pageCount = 0;
        pageOwnerUuid = null;
        pageTargetUuid = null;
        pageDimension = null;
        pageOwnerEpoch = 0L;
        // Keep the high-water mark so an old item cannot match a later tear.
        return changed;
    }

    /** Closes the temporary book, never the independently torn pages. */
    public boolean clear() {
        boolean changed = active
                || ownerUuid != null
                || targetUuid != null
                || dimension != null
                || expiresAt != 0L;
        active = false;
        ownerUuid = null;
        targetUuid = null;
        dimension = null;
        expiresAt = 0L;
        bookAmplifier = 0;
        resolveCountered = false;
        bookOwnerEpoch = 0L;
        return changed;
    }

    public void applyClientSnapshot(
            boolean newActive,
            @Nullable UUID newOwner,
            @Nullable UUID newTarget,
            @Nullable ResourceLocation newDimension,
            long newExpiresAt,
            int newPageCount,
            long newGeneration,
            int newBookAmplifier,
            @Nullable UUID newPageOwner) {
        clear();
        clearPages();
        pageCount = BookStateRules.clampPages(newPageCount);
        // Only the creator/count hint is needed by client ability conditions.
        pageOwnerUuid = pageCount > 0 ? newPageOwner : null;
        generation = Math.max(0L, newGeneration);
        if (!newActive
                || newOwner == null
                || newTarget == null
                || newDimension == null) {
            return;
        }
        active = true;
        ownerUuid = newOwner;
        targetUuid = newTarget;
        dimension = newDimension;
        expiresAt = Math.max(0L, newExpiresAt);
        bookAmplifier = Math.clamp(newBookAmplifier, 0, 2);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERSION_TAG, SERIALIZATION_VERSION);
        tag.putLong(GENERATION_TAG, generation);
        tag.putLong(PAGE_GENERATION_TAG, pageGeneration);
        if (hasPages()) {
            tag.putInt(PAGE_COUNT_TAG, pageCount);
            tag.putUUID(PAGE_OWNER_TAG, pageOwnerUuid);
            tag.putUUID(PAGE_TARGET_TAG, pageTargetUuid);
            tag.putString(PAGE_DIMENSION_TAG, pageDimension.toString());
            tag.putLong(PAGE_OWNER_EPOCH_TAG, pageOwnerEpoch);
        }
        if (!active()) {
            return tag;
        }
        tag.putBoolean(ACTIVE_TAG, true);
        tag.putUUID(OWNER_TAG, ownerUuid);
        tag.putUUID(TARGET_TAG, targetUuid);
        tag.putString(DIMENSION_TAG, dimension.toString());
        tag.putLong(EXPIRES_AT_TAG, expiresAt);
        tag.putInt(BOOK_AMPLIFIER_TAG, bookAmplifier);
        tag.putBoolean(RESOLVE_COUNTERED_TAG, resolveCountered);
        tag.putLong(BOOK_OWNER_EPOCH_TAG, bookOwnerEpoch);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clear();
        clearPages();
        generation = 0L;
        pageGeneration = 0L;
        int version = tag.getInt(VERSION_TAG);
        if (version < 1 || version > SERIALIZATION_VERSION) {
            return;
        }
        generation = version >= 2
                ? Math.max(0L, tag.getLong(GENERATION_TAG))
                : 0L;
        if (version >= 3) {
            pageGeneration = Math.max(0L, tag.getLong(PAGE_GENERATION_TAG));
            ResourceLocation origin = ResourceLocation.tryParse(
                    tag.getString(PAGE_DIMENSION_TAG));
            if (tag.hasUUID(PAGE_OWNER_TAG)
                    && tag.hasUUID(PAGE_TARGET_TAG)
                    && origin != null && pageGeneration > 0L) {
                pageOwnerUuid = tag.getUUID(PAGE_OWNER_TAG);
                pageTargetUuid = tag.getUUID(PAGE_TARGET_TAG);
                pageDimension = origin;
                pageOwnerEpoch = Math.max(0L, tag.getLong(PAGE_OWNER_EPOCH_TAG));
                pageCount = BookStateRules.clampPages(tag.getInt(PAGE_COUNT_TAG));
                if (pageCount == 0) {
                    clearPages();
                }
            }
        }
        if (!tag.getBoolean(ACTIVE_TAG)
                || !tag.hasUUID(OWNER_TAG)
                || !tag.hasUUID(TARGET_TAG)) {
            return;
        }
        ResourceLocation storedDimension =
                ResourceLocation.tryParse(
                        tag.getString(DIMENSION_TAG));
        if (storedDimension == null) {
            return;
        }
        active = true;
        ownerUuid = tag.getUUID(OWNER_TAG);
        targetUuid = tag.getUUID(TARGET_TAG);
        dimension = storedDimension;
        expiresAt = Math.max(0L, tag.getLong(EXPIRES_AT_TAG));
        bookAmplifier = version >= 2
                ? Math.clamp(tag.getInt(BOOK_AMPLIFIER_TAG), 0, 2)
                : 0;
        resolveCountered = version >= 4 && tag.getBoolean(RESOLVE_COUNTERED_TAG);
        bookOwnerEpoch = version >= 5 ? Math.max(0L, tag.getLong(BOOK_OWNER_EPOCH_TAG)) : 0L;
        if (generation == 0L) {
            generation = 1L;
        }
        if (version < 3) {
            pageCount = BookStateRules.clampPages(tag.getInt(PAGE_COUNT_TAG));
            if (pageCount > 0) {
                pageOwnerUuid = ownerUuid;
                pageTargetUuid = targetUuid;
                pageDimension = dimension;
                pageGeneration = generation;
            }
        }
    }
}

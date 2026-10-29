package rotp.addons.speedking.state;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class HeatVisionTargetState
        implements INBTSerializable<CompoundTag> {
    private final Map<UUID, Lease> leases = new LinkedHashMap<>();
    private boolean snapshotCaptured;
    private String originalTeamName;
    private MobEffectInstance originalGlowing;
    private int originalGlowingElapsed;
    private boolean glowingOwned;
    private long installedGlowingExpiresAt;
    private long observedGlowingGameTime;
    private int observedGlowingDuration;

    public void captureSnapshot(
            String teamName, MobEffectInstance glowing) {
        if (snapshotCaptured) {
            return;
        }
        snapshotCaptured = true;
        originalTeamName = teamName;
        originalGlowing = copy(glowing);
        originalGlowingElapsed = 0;
    }

    public boolean snapshotCaptured() {
        return snapshotCaptured;
    }

    public String originalTeamName() {
        return originalTeamName;
    }

    /**
     * Counts effect ticks the lease's own Glowing ran. 1.16 heat vision only
     * merged a 200-tick Glowing into the target's, so the original kept
     * counting down underneath it instead of pausing.
     */
    public void elapseOriginalGlowing(int ticks) {
        if (originalGlowing != null && ticks > 0) {
            originalGlowingElapsed = (int) Math.min(Integer.MAX_VALUE,
                    (long) originalGlowingElapsed + ticks);
        }
    }

    /** The original Glowing as it would stand now, or null once it has run out. */
    public MobEffectInstance remainingOriginalGlowing() {
        return remaining(copy(originalGlowing), originalGlowingElapsed);
    }

    public void putLease(UUID ownerUuid, UUID leaseId, long expiresAt) {
        leases.put(ownerUuid, new Lease(ownerUuid, leaseId, expiresAt));
    }

    public Lease removeLease(UUID ownerUuid) {
        return leases.remove(ownerUuid);
    }

    public boolean hasLease(UUID ownerUuid, UUID leaseId) {
        Lease lease = leases.get(ownerUuid);
        return lease != null && lease.leaseId().equals(leaseId);
    }

    public boolean hasLeases() {
        return !leases.isEmpty();
    }

    public List<Lease> leases() {
        return List.copyOf(leases.values());
    }

    public Lease lastLease() {
        Lease last = null;
        for (Lease lease : leases.values()) {
            last = lease;
        }
        return last;
    }

    public long maxExpiresAt() {
        long expiresAt = 0L;
        for (Lease lease : leases.values()) {
            expiresAt = Math.max(expiresAt, lease.expiresAt());
        }
        return expiresAt;
    }

    public boolean glowingOwned() {
        return glowingOwned;
    }

    public long installedGlowingExpiresAt() {
        return installedGlowingExpiresAt;
    }

    public long observedGlowingGameTime() {
        return observedGlowingGameTime;
    }

    public int observedGlowingDuration() {
        return observedGlowingDuration;
    }

    public void observeOwnedGlowing(
            long expiresAt, long gameTime, int duration) {
        glowingOwned = true;
        installedGlowingExpiresAt = expiresAt;
        observedGlowingGameTime = gameTime;
        observedGlowingDuration = duration;
    }

    public void clearGlowingOwnership() {
        glowingOwned = false;
        installedGlowingExpiresAt = 0L;
        observedGlowingGameTime = 0L;
        observedGlowingDuration = 0;
    }

    public void clearSnapshot() {
        leases.clear();
        snapshotCaptured = false;
        originalTeamName = null;
        originalGlowing = null;
        originalGlowingElapsed = 0;
        clearGlowingOwnership();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (!snapshotCaptured && leases.isEmpty()) {
            return tag;
        }
        tag.putBoolean("SnapshotCaptured", snapshotCaptured);
        if (originalTeamName != null) {
            tag.putString("OriginalTeam", originalTeamName);
        }
        if (originalGlowing != null) {
            MobEffectInstance.CODEC.encodeStart(
                            NbtOps.INSTANCE,
                            copy(originalGlowing))
                    .ifSuccess(encoded -> tag.put("OriginalGlowing", encoded));
        }
        tag.putInt("OriginalGlowingElapsed", originalGlowingElapsed);
        tag.putBoolean("GlowingOwned", glowingOwned);
        tag.putLong("InstalledGlowingExpiresAt", installedGlowingExpiresAt);
        tag.putLong("ObservedGlowingGameTime", observedGlowingGameTime);
        tag.putInt("ObservedGlowingDuration", observedGlowingDuration);
        ListTag serializedLeases = new ListTag();
        for (Lease lease : leases.values()) {
            CompoundTag leaseTag = new CompoundTag();
            leaseTag.putUUID("Owner", lease.ownerUuid());
            leaseTag.putUUID("Lease", lease.leaseId());
            leaseTag.putLong("ExpiresAt", lease.expiresAt());
            serializedLeases.add(leaseTag);
        }
        tag.put("Leases", serializedLeases);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        clearSnapshot();
        snapshotCaptured = tag.getBoolean("SnapshotCaptured");
        originalTeamName = tag.contains("OriginalTeam", Tag.TAG_STRING)
                ? tag.getString("OriginalTeam") : null;
        if (tag.contains("OriginalGlowing")) {
            originalGlowing = MobEffectInstance.CODEC.decode(
                            NbtOps.INSTANCE,
                            tag.get("OriginalGlowing"))
                    .result()
                    .map(Pair::getFirst)
                    .map(HeatVisionTargetState::copy)
                    .orElse(null);
        }
        originalGlowingElapsed = Math.max(
                0, tag.getInt("OriginalGlowingElapsed"));
        glowingOwned = tag.getBoolean("GlowingOwned");
        installedGlowingExpiresAt = tag.getLong(
                "InstalledGlowingExpiresAt");
        observedGlowingGameTime = tag.getLong("ObservedGlowingGameTime");
        observedGlowingDuration = tag.getInt("ObservedGlowingDuration");
        if (tag.contains("Leases", Tag.TAG_LIST)) {
            ListTag serializedLeases = tag.getList(
                    "Leases", Tag.TAG_COMPOUND);
            for (Tag raw : serializedLeases) {
                CompoundTag leaseTag = (CompoundTag) raw;
                if (leaseTag.hasUUID("Owner")
                        && leaseTag.hasUUID("Lease")) {
                    UUID ownerUuid = leaseTag.getUUID("Owner");
                    leases.put(ownerUuid, new Lease(
                            ownerUuid,
                            leaseTag.getUUID("Lease"),
                            leaseTag.getLong("ExpiresAt")));
                }
            }
        }
        if (leases.isEmpty()) {
            clearSnapshot();
        }
    }

    private static MobEffectInstance copy(MobEffectInstance effect) {
        if (effect == null) {
            return null;
        }
        MobEffectInstance copied = new MobEffectInstance(effect);
        copied.hiddenEffect = copy(effect.hiddenEffect);
        return copied;
    }

    private static MobEffectInstance remaining(MobEffectInstance effect, int elapsed) {
        if (effect == null) {
            return null;
        }
        effect.hiddenEffect = remaining(effect.hiddenEffect, elapsed);
        if (!effect.isInfiniteDuration()) {
            effect.duration = (int) Math.max(0L, (long) effect.getDuration() - elapsed);
        }
        return effect.getDuration() == 0 ? effect.hiddenEffect : effect;
    }

    public record Lease(UUID ownerUuid, UUID leaseId, long expiresAt) {}
}

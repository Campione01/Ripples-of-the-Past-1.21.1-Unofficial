package rotp.addons.killerqueenlion.btd;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-persistent authority for the one-owner-per-victim Bites the Dust
 * lease. Player attachments alone cannot arbitrate owners while they are
 * offline.
 */
public final class BitesTheDustVictimIndex extends SavedData {
    private static final String FILE_NAME =
            "rotp_zkq8-bites_the_dust_victim_index";
    private static final String BINDINGS_TAG = "Bindings";
    private static final String REVOKED_TAG = "Revoked";
    private static final int MAX_BINDINGS = 512;
    private static final SavedData.Factory<BitesTheDustVictimIndex>
            FACTORY = new SavedData.Factory<>(
                    BitesTheDustVictimIndex::new,
                    BitesTheDustVictimIndex::load);

    private final Map<UUID, Binding> byVictim = new HashMap<>();
    private final Map<UUID, Binding> byOwner = new HashMap<>();
    private final Map<UUID, UUID> revokedMarkerEpochByOwner =
            new HashMap<>();

    public enum ReconcileResult {
        ACCEPTED,
        CONFLICT,
        REVOKED
    }

    public static BitesTheDustVictimIndex get(
            MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(FACTORY, FILE_NAME);
    }

    public static BitesTheDustVictimIndex load(
            CompoundTag tag, HolderLookup.Provider registries) {
        BitesTheDustVictimIndex data =
                new BitesTheDustVictimIndex();
        for (Tag value : tag.getList(
                BINDINGS_TAG, Tag.TAG_COMPOUND)) {
            if (!(value instanceof CompoundTag entry)
                    || data.byVictim.size() >= MAX_BINDINGS) {
                continue;
            }
            Binding binding = Binding.fromTag(entry);
            if (binding != null) {
                data.insert(binding);
            }
        }
        for (Tag value : tag.getList(
                REVOKED_TAG, Tag.TAG_COMPOUND)) {
            if (!(value instanceof CompoundTag entry)
                    || !entry.hasUUID("Owner")
                    || !entry.hasUUID("MarkerEpoch")
                    || data.revokedMarkerEpochByOwner.size()
                            >= MAX_BINDINGS) {
                continue;
            }
            data.revokedMarkerEpochByOwner.put(
                    entry.getUUID("Owner"),
                    entry.getUUID("MarkerEpoch"));
        }
        return data;
    }

    @Override
    public synchronized CompoundTag save(
            CompoundTag tag, HolderLookup.Provider registries) {
        ListTag bindings = new ListTag();
        byVictim.values().stream()
                .sorted(Comparator.comparing(
                        binding -> binding.victimUuid().toString()))
                .map(Binding::toTag)
                .forEach(bindings::add);
        tag.put(BINDINGS_TAG, bindings);

        ListTag revoked = new ListTag();
        revokedMarkerEpochByOwner.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CompoundTag value = new CompoundTag();
                    value.putUUID("Owner", entry.getKey());
                    value.putUUID("MarkerEpoch", entry.getValue());
                    revoked.add(value);
                });
        tag.put(REVOKED_TAG, revoked);
        return tag;
    }

    public synchronized ReconcileResult reconcile(
            UUID ownerUuid,
            UUID victimUuid,
            ResourceLocation victimDimension,
            UUID markerEpoch,
            ResourceLocation markerDimension,
            long markerGameTime) {
        if (ownerUuid == null
                || victimUuid == null
                || victimDimension == null
                || markerEpoch == null
                || markerDimension == null
                || markerGameTime < 0L) {
            return ReconcileResult.CONFLICT;
        }
        if (markerEpoch.equals(
                revokedMarkerEpochByOwner.get(ownerUuid))) {
            return ReconcileResult.REVOKED;
        }
        Binding occupied = byVictim.get(victimUuid);
        if (occupied != null
                && !ownerUuid.equals(occupied.ownerUuid())) {
            return ReconcileResult.CONFLICT;
        }
        Binding replacement = new Binding(
                ownerUuid,
                victimUuid,
                victimDimension,
                markerEpoch,
                markerDimension,
                markerGameTime);
        Binding previous = byOwner.get(ownerUuid);
        if (replacement.equals(previous)
                && replacement.equals(occupied)) {
            return ReconcileResult.ACCEPTED;
        }
        if (previous != null) {
            removeBinding(previous);
        }
        if (occupied != null) {
            removeBinding(occupied);
        }
        if (byVictim.size() >= MAX_BINDINGS) {
            if (previous != null) {
                insert(previous);
            }
            return ReconcileResult.CONFLICT;
        }
        insert(replacement);
        revokedMarkerEpochByOwner.remove(ownerUuid);
        setDirty();
        return ReconcileResult.ACCEPTED;
    }

    public synchronized boolean releaseOwner(UUID ownerUuid) {
        Binding binding = byOwner.get(ownerUuid);
        boolean changed = binding != null;
        if (binding != null) {
            removeBinding(binding);
        }
        changed = revokedMarkerEpochByOwner.remove(ownerUuid) != null
                || changed;
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public synchronized boolean revokeVictim(UUID victimUuid) {
        Binding binding = byVictim.get(victimUuid);
        if (binding == null) {
            return false;
        }
        removeBinding(binding);
        revokedMarkerEpochByOwner.put(
                binding.ownerUuid(), binding.markerEpoch());
        setDirty();
        return true;
    }

    /** Live victim observation wins over an attachment's stale dimension. */
    @Nullable
    public synchronized Binding observeVictimDimension(
            UUID victimUuid, ResourceLocation dimension) {
        Binding binding = byVictim.get(victimUuid);
        if (binding == null || dimension == null) {
            return binding;
        }
        if (binding.victimDimension().equals(dimension)) {
            return binding;
        }
        removeBinding(binding);
        Binding updated = new Binding(
                binding.ownerUuid(),
                binding.victimUuid(),
                dimension,
                binding.markerEpoch(),
                binding.markerDimension(),
                binding.markerGameTime());
        insert(updated);
        setDirty();
        return updated;
    }

    /** Expires leases even when their owners are offline. */
    public synchronized int expireBindings(MinecraftServer server) {
        int expired = 0;
        for (Binding binding : List.copyOf(byVictim.values())) {
            ServerLevel markerLevel = server.getLevel(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries
                                    .DIMENSION,
                            binding.markerDimension()));
            if (markerLevel == null
                    || markerLevel.getGameTime()
                            - binding.markerGameTime()
                            >= BitesTheDustService
                                    .MARK_LIFETIME_TICKS) {
                expire(binding);
                expired++;
            }
        }
        return expired;
    }

    synchronized int expireBindings(
            ResourceLocation markerDimension,
            long currentGameTime) {
        int expired = 0;
        for (Binding binding : List.copyOf(byVictim.values())) {
            if (binding.markerDimension().equals(markerDimension)
                    && currentGameTime - binding.markerGameTime()
                            >= BitesTheDustService
                                    .MARK_LIFETIME_TICKS) {
                expire(binding);
                expired++;
            }
        }
        return expired;
    }

    @Nullable
    public synchronized Binding bindingForVictim(
            UUID victimUuid, ResourceLocation dimension) {
        Binding binding = byVictim.get(victimUuid);
        return binding != null
                && binding.victimDimension().equals(dimension)
                        ? binding
                        : null;
    }

    @Nullable
    public synchronized Binding bindingForOwner(UUID ownerUuid) {
        return byOwner.get(ownerUuid);
    }

    private void insert(Binding binding) {
        Binding displacedVictim = byVictim.put(
                binding.victimUuid(), binding);
        if (displacedVictim != null
                && !displacedVictim.ownerUuid()
                        .equals(binding.ownerUuid())) {
            byOwner.remove(displacedVictim.ownerUuid());
        }
        Binding displacedOwner = byOwner.put(
                binding.ownerUuid(), binding);
        if (displacedOwner != null
                && !displacedOwner.victimUuid()
                        .equals(binding.victimUuid())) {
            byVictim.remove(displacedOwner.victimUuid());
        }
    }

    private void removeBinding(Binding binding) {
        byVictim.remove(binding.victimUuid(), binding);
        byOwner.remove(binding.ownerUuid(), binding);
    }

    private void expire(Binding binding) {
        removeBinding(binding);
        revokedMarkerEpochByOwner.put(
                binding.ownerUuid(), binding.markerEpoch());
        setDirty();
    }

    public record Binding(
            UUID ownerUuid,
            UUID victimUuid,
            ResourceLocation victimDimension,
            UUID markerEpoch,
            ResourceLocation markerDimension,
            long markerGameTime) {
        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", ownerUuid);
            tag.putUUID("Victim", victimUuid);
            tag.putString("VictimDimension", victimDimension.toString());
            tag.putUUID("MarkerEpoch", markerEpoch);
            tag.putString(
                    "MarkerDimension", markerDimension.toString());
            tag.putLong("MarkerGameTime", markerGameTime);
            return tag;
        }

        @Nullable
        private static Binding fromTag(CompoundTag tag) {
            if (!tag.hasUUID("Owner")
                    || !tag.hasUUID("Victim")
                    || !tag.hasUUID("MarkerEpoch")
                    || !tag.contains("MarkerGameTime")) {
                return null;
            }
            ResourceLocation victimDimension = ResourceLocation.tryParse(
                    tag.getString("VictimDimension"));
            ResourceLocation markerDimension = ResourceLocation.tryParse(
                    tag.getString("MarkerDimension"));
            long markerGameTime = tag.getLong("MarkerGameTime");
            return victimDimension != null
                            && markerDimension != null
                            && markerGameTime >= 0L
                    ? new Binding(
                            tag.getUUID("Owner"),
                            tag.getUUID("Victim"),
                            victimDimension,
                            tag.getUUID("MarkerEpoch"),
                            markerDimension,
                            markerGameTime)
                    : null;
        }
    }
}

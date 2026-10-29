package rotp.addons.littlefeet.scale;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class LittleFeetScaleOwnership {
    private final Map<UUID, Map<UUID, Long>> targetOwners =
            new LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> ownerTargets =
            new LinkedHashMap<>();
    // Owner -> the Stand instance its leases were cut with.
    private final Map<UUID, Object> standBindings =
            new LinkedHashMap<>();

    void bindStand(UUID ownerId, Object stand) {
        if (ownerTargets.containsKey(ownerId)) {
            standBindings.put(ownerId, stand);
        }
    }

    // 1.16 cut targets with a Stand effect that a Stand change stopped
    // (/stand give ... true). Compared by identity: the core swaps the
    // Stand instance on every change. Clone and leave already clear.
    boolean isStandReplaced(UUID ownerId, Object stand) {
        Object bound = standBindings.get(ownerId);
        return bound != null && bound != stand;
    }

    boolean track(
            UUID ownerId, UUID targetId, long nextShrinkTick) {
        Map<UUID, Long> owners = targetOwners.computeIfAbsent(
                targetId, unused -> new LinkedHashMap<>());
        if (owners.containsKey(ownerId)) {
            return false;
        }
        owners.put(ownerId, nextShrinkTick);
        ownerTargets.computeIfAbsent(
                        ownerId, unused -> new LinkedHashSet<>())
                .add(targetId);
        return true;
    }

    Set<TargetLease> leases(UUID targetId) {
        Map<UUID, Long> owners = targetOwners.get(targetId);
        if (owners == null) {
            return Set.of();
        }
        Set<TargetLease> leases = new LinkedHashSet<>();
        owners.forEach((ownerId, nextShrinkTick) ->
                leases.add(new TargetLease(
                        ownerId, nextShrinkTick)));
        return Set.copyOf(leases);
    }

    boolean advance(
            UUID ownerId, UUID targetId, long nextShrinkTick) {
        Map<UUID, Long> owners = targetOwners.get(targetId);
        if (owners == null || !owners.containsKey(ownerId)) {
            return false;
        }
        owners.put(ownerId, nextShrinkTick);
        return true;
    }

    Set<UUID> targets(UUID ownerId) {
        Set<UUID> owned = ownerTargets.get(ownerId);
        return owned != null ? Set.copyOf(owned) : Set.of();
    }

    boolean remove(UUID ownerId, UUID targetId) {
        Map<UUID, Long> owners = targetOwners.get(targetId);
        if (owners == null || owners.remove(ownerId) == null) {
            return false;
        }
        if (owners.isEmpty()) {
            targetOwners.remove(targetId);
        }
        Set<UUID> owned = ownerTargets.get(ownerId);
        if (owned != null) {
            owned.remove(targetId);
            if (owned.isEmpty()) {
                dropOwner(ownerId);
            }
        }
        return true;
    }

    Set<UUID> removeTarget(UUID targetId) {
        Map<UUID, Long> removed = targetOwners.remove(targetId);
        if (removed == null) {
            return Set.of();
        }
        for (UUID ownerId : removed.keySet()) {
            Set<UUID> owned = ownerTargets.get(ownerId);
            if (owned != null) {
                owned.remove(targetId);
                if (owned.isEmpty()) {
                    dropOwner(ownerId);
                }
            }
        }
        return Set.copyOf(removed.keySet());
    }

    Set<UUID> removeOwner(UUID ownerId) {
        standBindings.remove(ownerId);
        Set<UUID> owned = ownerTargets.remove(ownerId);
        if (owned == null) {
            return Set.of();
        }
        Set<UUID> removedTargets = new LinkedHashSet<>();
        for (UUID targetId : owned) {
            Map<UUID, Long> owners = targetOwners.get(targetId);
            if (owners != null && owners.remove(ownerId) != null) {
                removedTargets.add(targetId);
                if (owners.isEmpty()) {
                    targetOwners.remove(targetId);
                }
            }
        }
        return Set.copyOf(removedTargets);
    }

    boolean hasTarget(UUID targetId) {
        return targetOwners.containsKey(targetId);
    }

    boolean hasOwner(UUID ownerId) {
        return ownerTargets.containsKey(ownerId);
    }

    int targetCount(UUID ownerId) {
        Set<UUID> owned = ownerTargets.get(ownerId);
        return owned != null ? owned.size() : 0;
    }

    void clear() {
        targetOwners.clear();
        ownerTargets.clear();
        standBindings.clear();
    }

    private void dropOwner(UUID ownerId) {
        ownerTargets.remove(ownerId);
        standBindings.remove(ownerId);
    }

    record TargetLease(UUID ownerId, long nextShrinkTick) {}
}

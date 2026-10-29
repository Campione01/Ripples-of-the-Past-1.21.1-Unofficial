package rotp.addons.ger.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * Canon-guided gameplay extrapolation for GER against core time stop. The
 * manga does not establish this interaction, so the policy is intentionally
 * limited to post-mark time stops whose areas intersect the rewind region.
 */
final class GerRtzTimeStopPolicy {
    private GerRtzTimeStopPolicy() {}

    static final class GenerationTracker<T> {
        private final Map<Integer, ActiveGeneration<T>> active =
                new HashMap<>();
        private long nextGeneration = 1L;

        InstanceKey added(int id, T instance) {
            ActiveGeneration<T> current = active.get(id);
            if (current != null
                    && current.awaitingAdded()
                    && current.instance() == instance) {
                active.put(
                        id,
                        new ActiveGeneration<>(
                                current.key(), instance, false));
                return current.key();
            }
            return allocate(id, instance, false);
        }

        InstanceKey observe(int id, T instance) {
            ActiveGeneration<T> current = active.get(id);
            if (current != null) {
                active.put(
                        id,
                        new ActiveGeneration<>(
                                current.key(),
                                instance,
                                current.awaitingAdded()));
                return current.key();
            }
            return allocate(id, instance, true);
        }

        void removed(int id, boolean replacementActive) {
            if (!replacementActive) {
                active.remove(id);
            }
        }

        boolean isEmpty() {
            return active.isEmpty();
        }

        private InstanceKey allocate(
                int id, T instance, boolean awaitingAdded) {
            InstanceKey key = new InstanceKey(
                    id, nextGeneration++);
            active.put(
                    id,
                    new ActiveGeneration<>(
                            key, instance, awaitingAdded));
            return key;
        }

        private record ActiveGeneration<T>(
                InstanceKey key,
                T instance,
                boolean awaitingAdded) {}
    }

    static void terminatePostMarkInstances(
            Set<InstanceKey> instancesAtMark,
            int markCenterChunkX,
            int markCenterChunkZ,
            int markChunkRadius,
            Collection<InstanceRegion> currentInstances,
            IntConsumer removeInstance) {
        Objects.requireNonNull(removeInstance);
        idsToTerminate(
                instancesAtMark,
                markCenterChunkX,
                markCenterChunkZ,
                markChunkRadius,
                currentInstances)
                .forEach(removeInstance::accept);
    }

    static List<Integer> idsToTerminate(
            Set<InstanceKey> instancesAtMark,
            int markCenterChunkX,
            int markCenterChunkZ,
            int markChunkRadius,
            Collection<InstanceRegion> currentInstances) {
        Objects.requireNonNull(instancesAtMark);
        Objects.requireNonNull(currentInstances);
        int normalizedMarkRadius = Math.max(0, markChunkRadius);
        return currentInstances.stream()
                .filter(Objects::nonNull)
                .filter(instance -> shouldTerminate(
                        instancesAtMark,
                        markCenterChunkX,
                        markCenterChunkZ,
                        normalizedMarkRadius,
                        instance))
                .map(InstanceRegion::id)
                .distinct()
                .sorted()
                .toList();
    }

    static boolean shouldTerminate(
            Set<InstanceKey> instancesAtMark,
            int markCenterChunkX,
            int markCenterChunkZ,
            int markChunkRadius,
            InstanceRegion instance) {
        Objects.requireNonNull(instancesAtMark);
        Objects.requireNonNull(instance);
        return !instancesAtMark.contains(instance.key())
                && intersectsMark(
                        markCenterChunkX,
                        markCenterChunkZ,
                        Math.max(0, markChunkRadius),
                        instance);
    }

    private static boolean intersectsMark(
            int markCenterChunkX,
            int markCenterChunkZ,
            int markChunkRadius,
            InstanceRegion instance) {
        if (instance.chunkRange() <= 0) {
            return true;
        }
        long combinedReach = (long) markChunkRadius
                + instance.chunkRange() - 1L;
        long chunkDistanceX = Math.abs(
                (long) instance.centerChunkX()
                        - markCenterChunkX);
        long chunkDistanceZ = Math.abs(
                (long) instance.centerChunkZ()
                        - markCenterChunkZ);
        return chunkDistanceX <= combinedReach
                && chunkDistanceZ <= combinedReach;
    }

    record InstanceKey(int id, long generation) {}

    record InstanceRegion(
            InstanceKey key,
            int centerChunkX,
            int centerChunkZ,
            int chunkRange) {
        int id() {
            return key.id();
        }
    }
}

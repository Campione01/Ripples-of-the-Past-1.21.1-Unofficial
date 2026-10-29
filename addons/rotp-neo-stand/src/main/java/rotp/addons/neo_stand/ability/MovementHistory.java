package rotp.addons.neo_stand.ability;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MovementHistory {
    private static final int MAX_POINTS = 256;
    private static final double MIN_STEP_SQR = 0.05D * 0.05D;
    private static final Map<UUID, Deque<Vec3>> HISTORY =
            new HashMap<>();

    private MovementHistory() {}

    public static void record(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            clear(entity.getUUID());
            return;
        }
        Deque<Vec3> points = HISTORY.computeIfAbsent(
                entity.getUUID(), ignored -> new ArrayDeque<>());
        Vec3 current = entity.position();
        Vec3 last = points.peekLast();
        if (last == null || last.distanceToSqr(current) >= MIN_STEP_SQR) {
            points.addLast(current);
            while (points.size() > MAX_POINTS) {
                points.removeFirst();
            }
        }
    }

    public static Vec3 positionAgo(
            LivingEntity entity, double distance) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return null;
        }
        Deque<Vec3> points = HISTORY.get(entity.getUUID());
        if (points == null || points.size() < 2) {
            return null;
        }
        Vec3[] path = points.toArray(Vec3[]::new);
        double traversed = 0.0D;
        for (int index = path.length - 1; index > 0; index--) {
            Vec3 current = path[index];
            Vec3 previous = path[index - 1];
            double segment = current.distanceTo(previous);
            if (segment <= 1.0E-6D) {
                continue;
            }
            if (traversed + segment >= distance) {
                return current.lerp(
                        previous,
                        (distance - traversed) / segment);
            }
            traversed += segment;
        }
        return traversed > 0.05D ? path[0] : null;
    }

    public static void clear(UUID entityId) {
        HISTORY.remove(entityId);
    }

    public static void clearAll() {
        HISTORY.clear();
    }
}

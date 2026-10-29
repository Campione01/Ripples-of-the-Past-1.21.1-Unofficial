package rotp.addons.kingcrimson.state;

import java.util.List;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.KingCrimsonEpitaphForesightSpec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * What Epitaph foresaw for one creature, which the creature is now bound to (see {@link EpitaphForesightService}).
 * Held by the Epitaph user's {@link EpitaphState}, on the server only, and never saved.
 */
public final class EpitaphFate {
    final ResourceKey<Level> dimension;
    final Vec3 foreseen;
    final Vec3 origin;
    private final List<Vec3> course;
    private final double courseLength;
    /** Blocks per tick the creature was moving at when its fate was foreseen. */
    final double speed;
    /** The navigation path the creature keeps to, or null when it is only carried by its momentum. */
    @Nullable
    final Path path;
    final double speedModifier;
    final long committedAt;
    /** The foreseen moment: when the creature stands where Epitaph showed it. */
    final long arrivesAt;

    EpitaphFate(ResourceKey<Level> dimension, List<Vec3> course, double speed, @Nullable Path path,
            double speedModifier, long committedAt, long arrivesAt) {
        this.dimension = dimension;
        this.course = List.copyOf(course);
        this.origin = this.course.getFirst();
        this.foreseen = this.course.getLast();
        double length = 0.0D;
        for (int i = 1; i < this.course.size(); i++) {
            length += this.course.get(i - 1).distanceTo(this.course.get(i));
        }
        this.courseLength = length;
        this.speed = speed;
        this.path = path;
        this.speedModifier = speedModifier;
        this.committedAt = committedAt;
        this.arrivesAt = arrivesAt;
    }

    /** Whether the creature is still bound: its fate has not been fulfilled, run out, or broken by force. */
    boolean holds(Entity target, long gameTime) {
        if (!target.isAlive() || target.level().dimension() != dimension || target.isPassenger()) {
            return false;
        }
        double breakDistanceSqr = KingCrimsonEpitaphForesightSpec.FATE_BREAK_DISTANCE
                * KingCrimsonEpitaphForesightSpec.FATE_BREAK_DISTANCE;
        return gameTime <= arrivesAt + KingCrimsonEpitaphForesightSpec.FATE_GRACE_TICKS
                && target.position().distanceToSqr(courseAt(gameTime)) <= breakDistanceSqr;
    }

    boolean isFulfilled(Entity target, long gameTime) {
        return gameTime >= arrivesAt && target.position().distanceToSqr(foreseen) <= 1.0E-4D;
    }

    /** Walks the immutable foreseen course, including its corners and final point. */
    Vec3 courseAt(long gameTime) {
        long elapsed = Math.max(0L, gameTime - committedAt);
        long total = Math.max(1L, arrivesAt - committedAt);
        double remaining = courseLength * Math.min(1.0D, elapsed / (double) total);
        for (int i = 1; i < course.size(); i++) {
            Vec3 from = course.get(i - 1);
            Vec3 to = course.get(i);
            double distance = from.distanceTo(to);
            if (distance > 0.0D && remaining <= distance) {
                return from.lerp(to, remaining / distance);
            }
            remaining -= distance;
        }
        return foreseen;
    }
}

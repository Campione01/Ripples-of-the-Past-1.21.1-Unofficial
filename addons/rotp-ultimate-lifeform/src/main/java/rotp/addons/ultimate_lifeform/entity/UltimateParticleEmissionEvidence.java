package rotp.addons.ultimate_lifeform.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

/**
 * Accumulates particles that were actually submitted to the client level.
 */
public final class UltimateParticleEmissionEvidence {
    private static final ThreadLocal<PendingAttempt> PENDING =
            new ThreadLocal<>();
    @Nullable private String resource;
    private int count;
    private long nextSequence;
    private double minX;
    private double minY;
    private double minZ;
    private double maxX;
    private double maxY;
    private double maxZ;
    private double lastX;
    private double lastY;
    private double lastZ;
    private final List<EmissionPoint> points = new ArrayList<>();

    public PendingAttempt begin(
            UltimateAreaEffectEntity entity,
            String particleResource,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        if (PENDING.get() != null) {
            throw new IllegalStateException(
                    "Nested particle evidence submissions are forbidden.");
        }
        requireFinite(
                x, y, z, velocityX, velocityY, velocityZ);
        PendingAttempt attempt = new PendingAttempt(
                entity,
                ++nextSequence,
                particleResource,
                entity.level().getGameTime(),
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ);
        PENDING.set(attempt);
        return attempt;
    }

    public boolean complete(PendingAttempt attempt) {
        PendingAttempt current = PENDING.get();
        if (attempt == null || current != attempt) {
            PENDING.remove();
            if (current != null) {
                current.cancel();
            }
            return false;
        }
        PENDING.remove();
        DownstreamPointTicket ticket = attempt.takeTicket();
        if (ticket == null) {
            return false;
        }
        recordAccepted(new EmissionPoint(
                attempt.sequence(),
                attempt.resource(),
                attempt.clientGameTime(),
                attempt.x(),
                attempt.y(),
                attempt.z(),
                attempt.velocityX(),
                attempt.velocityY(),
                attempt.velocityZ(),
                ticket));
        return true;
    }

    public void abort(PendingAttempt attempt) {
        PendingAttempt current = PENDING.get();
        if (attempt == null || current != attempt) {
            PENDING.remove();
            if (current != null) {
                current.cancel();
            }
            return;
        }
        PENDING.remove();
        attempt.cancel();
    }

    @Nullable
    public static PendingAttempt pending(
            Object levelIdentity,
            String particleResource,
            long clientGameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        PendingAttempt attempt = PENDING.get();
        return attempt != null
                && attempt.entity().level() == levelIdentity
                && attempt.resource().equals(particleResource)
                && attempt.clientGameTime() == clientGameTime
                && same(attempt.x(), x)
                && same(attempt.y(), y)
                && same(attempt.z(), z)
                && same(attempt.velocityX(), velocityX)
                && same(attempt.velocityY(), velocityY)
                && same(attempt.velocityZ(), velocityZ)
                        ? attempt
                        : null;
    }

    public static void clearPendingDownstream() {
        PendingAttempt attempt = PENDING.get();
        PENDING.remove();
        if (attempt != null) {
            attempt.cancel();
        }
    }

    public void record(
            String particleResource,
            double x,
            double y,
            double z) {
        record(
                particleResource,
                Long.MIN_VALUE,
                x,
                y,
                z,
                0.0D,
                0.0D,
                0.0D);
    }

    public void record(
            String particleResource,
            long clientGameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        requireFinite(
                x, y, z, velocityX, velocityY, velocityZ);
        recordAccepted(new EmissionPoint(
                ++nextSequence,
                particleResource,
                clientGameTime,
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ,
                null));
    }

    private void recordAccepted(EmissionPoint point) {
        if (resource != null && !resource.equals(point.resource())) {
            throw new IllegalStateException(
                    "A particle evidence batch cannot mix resources.");
        }
        resource = point.resource();
        if (count == 0) {
            minX = maxX = point.x();
            minY = maxY = point.y();
            minZ = maxZ = point.z();
        }
        else {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        lastX = point.x();
        lastY = point.y();
        lastZ = point.z();
        points.add(point);
        count++;
    }

    @Nullable
    public EmissionBatch drain() {
        if (resource == null || count == 0) {
            return null;
        }
        EmissionBatch batch = new EmissionBatch(
                resource,
                count,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                lastX,
                lastY,
                lastZ,
                points);
        resource = null;
        count = 0;
        points.clear();
        return batch;
    }

    public record EmissionPoint(
            long sequence,
            String resource,
            long clientGameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            @Nullable DownstreamPointTicket downstreamTicket) {}

    public record DownstreamPointTicket(
            Object identity,
            long id,
            UUID ownerUuid,
            UUID entityUuid,
            int entityRuntimeId,
            Object entityIdentity,
            Object levelIdentity,
            String dimension,
            long sessionEpoch,
            long sceneEpoch,
            String sceneId,
            long frameNanos,
            String evidenceDigest) {}

    public record EmissionBatch(
            String resource,
            int count,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            double lastX,
            double lastY,
            double lastZ,
            List<EmissionPoint> points) {
        public EmissionBatch {
            points = List.copyOf(points);
        }

        public String evidenceToken() {
            return String.format(
                    Locale.ROOT,
                    "%s|count=%d|min=%.5f,%.5f,%.5f|max=%.5f,%.5f,%.5f",
                    resource,
                    count,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ);
        }
    }

    public static final class PendingAttempt {
        private final UltimateAreaEffectEntity entity;
        private final long sequence;
        private final String resource;
        private final long clientGameTime;
        private final double x;
        private final double y;
        private final double z;
        private final double velocityX;
        private final double velocityY;
        private final double velocityZ;
        @Nullable private DownstreamPointTicket ticket;
        @Nullable private Runnable cancellation;

        private PendingAttempt(
                UltimateAreaEffectEntity entity,
                long sequence,
                String resource,
                long clientGameTime,
                double x,
                double y,
                double z,
                double velocityX,
                double velocityY,
                double velocityZ) {
            this.entity = entity;
            this.sequence = sequence;
            this.resource = resource;
            this.clientGameTime = clientGameTime;
            this.x = x;
            this.y = y;
            this.z = z;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
            this.velocityZ = velocityZ;
        }

        public synchronized boolean acknowledge(
                DownstreamPointTicket downstreamTicket,
                Runnable cancellation) {
            if (ticket != null
                    || downstreamTicket == null
                    || cancellation == null) {
                return false;
            }
            ticket = downstreamTicket;
            this.cancellation = cancellation;
            return true;
        }

        @Nullable
        private synchronized DownstreamPointTicket takeTicket() {
            DownstreamPointTicket current = ticket;
            ticket = null;
            cancellation = null;
            return current;
        }

        private synchronized void cancel() {
            Runnable current = cancellation;
            ticket = null;
            cancellation = null;
            if (current != null) {
                current.run();
            }
        }

        public UltimateAreaEffectEntity entity() {
            return entity;
        }

        public long sequence() {
            return sequence;
        }

        public String resource() {
            return resource;
        }

        public long clientGameTime() {
            return clientGameTime;
        }

        public double x() {
            return x;
        }

        public double y() {
            return y;
        }

        public double z() {
            return z;
        }

        public double velocityX() {
            return velocityX;
        }

        public double velocityY() {
            return velocityY;
        }

        public double velocityZ() {
            return velocityZ;
        }
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Particle evidence requires finite coordinates.");
            }
        }
    }

    private static boolean same(double first, double second) {
        return Double.doubleToLongBits(first)
                == Double.doubleToLongBits(second);
    }
}

package rotp.addons.ultimate_lifeform.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

final class DownstreamTicketAuthority {
    enum Operation {
        REGISTER_GEOMETRY,
        REGISTER_PARTICLE_POINT,
        COMBINE_PARTICLE_BATCH,
        BIND_RECEIPT,
        CONSUME_RECEIPT
    }

    @FunctionalInterface
    interface OperationGuard {
        boolean allows(Operation operation);
    }

    private final OperationGuard guard;
    private final int maximumTickets;
    private final IdentityHashMap<Object, PendingRegistration> pending =
            new IdentityHashMap<>();
    private final IdentityHashMap<Object, BoundRegistration> bound =
            new IdentityHashMap<>();
    private final ArrayDeque<Object> registrationOrder =
            new ArrayDeque<>();
    private long nextTicketId = 1L;

    DownstreamTicketAuthority(
            OperationGuard guard, int maximumTickets) {
        this.guard = Objects.requireNonNull(guard);
        this.maximumTickets = Math.max(maximumTickets, 1);
    }

    @Nullable
    synchronized Ticket register(
            Operation operation,
            SubmissionContext context,
            SubmissionEvidence evidence) {
        if (!guard.allows(operation)) {
            return null;
        }
        return registerTrusted(context, evidence);
    }

    @Nullable
    synchronized Ticket combineParticleBatch(
            SubmissionContext batchContext,
            ParticleBatchEvidence batchEvidence,
            List<PointClaim> claims) {
        List<PointClaim> immutableClaims = List.copyOf(claims);
        List<PendingRegistration> registrations = new ArrayList<>(
                immutableClaims.size());
        for (PointClaim claim : immutableClaims) {
            if (claim != null && claim.ticket() != null) {
                Object identity = claim.ticket().identity();
                registrations.add(pending.remove(identity));
                registrationOrder.remove(identity);
            }
            else {
                registrations.add(null);
            }
        }
        if (!guard.allows(Operation.COMBINE_PARTICLE_BATCH)
                || immutableClaims.isEmpty()
                || immutableClaims.size() != batchEvidence.points().size()) {
            return null;
        }
        long previousSequence = Long.MIN_VALUE;
        for (int index = 0; index < immutableClaims.size(); index++) {
            PointClaim claim = immutableClaims.get(index);
            PendingRegistration registration = registrations.get(index);
            ParticlePointEvidence expected = batchEvidence.points().get(index);
            if (claim == null
                    || claim.ticket() == null
                    || registration == null
                    || registration.ticketId() != claim.ticket().id()
                    || !registration.context().matches(claim.context())
                    || !registration.evidence().equals(claim.evidence())
                    || !(claim.evidence()
                            instanceof ParticlePointEvidence point)
                    || !point.equals(expected)
                    || point.sequence() <= previousSequence
                    || !sameParticleOrigin(
                            registration.context(), batchContext)) {
                return null;
            }
            previousSequence = point.sequence();
        }
        return registerTrusted(batchContext, batchEvidence);
    }

    synchronized boolean bind(
            Ticket ticket,
            SubmissionContext context,
            SubmissionEvidence evidence,
            Object receiptFingerprint) {
        PendingRegistration registration = ticket != null
                ? pending.remove(ticket.identity())
                : null;
        if (!guard.allows(Operation.BIND_RECEIPT)
                || ticket == null
                || registration == null
                || registration.ticketId() != ticket.id()
                || !registration.context().matches(context)
                || !registration.evidence().equals(evidence)) {
            if (ticket != null) {
                registrationOrder.remove(ticket.identity());
            }
            return false;
        }
        bound.put(
                ticket.identity(),
                new BoundRegistration(
                        ticket.id(),
                        context,
                        evidence,
                        receiptFingerprint));
        return true;
    }

    @Nullable
    synchronized String consume(
            Ticket ticket,
            SubmissionContext expectedContext,
            Object receiptFingerprint) {
        BoundRegistration registration = ticket != null
                ? bound.remove(ticket.identity())
                : null;
        if (ticket != null) {
            registrationOrder.remove(ticket.identity());
        }
        if (!guard.allows(Operation.CONSUME_RECEIPT)) {
            return "spatial_submission_caller_mismatch";
        }
        if (ticket == null
                || registration == null
                || registration.ticketId() != ticket.id()) {
            return "spatial_submission_ticket_missing";
        }
        if (!registration.context().matches(expectedContext)) {
            return "spatial_submission_context_mismatch";
        }
        if (!registration.receiptFingerprint().equals(
                receiptFingerprint)) {
            return "spatial_submission_fingerprint_mismatch";
        }
        if (!registration.evidence().digest().equals(
                expectedContext.evidenceDigest())) {
            return "spatial_submission_evidence_mismatch";
        }
        return null;
    }

    synchronized void clear() {
        pending.clear();
        bound.clear();
        registrationOrder.clear();
    }

    synchronized void discard(@Nullable Ticket ticket) {
        if (ticket == null) {
            return;
        }
        pending.remove(ticket.identity());
        bound.remove(ticket.identity());
        registrationOrder.remove(ticket.identity());
    }

    synchronized int pendingCount() {
        return pending.size();
    }

    synchronized int boundCount() {
        return bound.size();
    }

    private Ticket registerTrusted(
            SubmissionContext context,
            SubmissionEvidence evidence) {
        if (!context.evidenceDigest().equals(evidence.digest())) {
            return null;
        }
        if (nextTicketId <= 0L) {
            clear();
            nextTicketId = 1L;
        }
        Object identity = new Object();
        long id = nextTicketId++;
        pending.put(identity, new PendingRegistration(id, context, evidence));
        registrationOrder.addLast(identity);
        prune();
        return new Ticket(identity, id);
    }

    private void prune() {
        while (pending.size() + bound.size() > maximumTickets
                && !registrationOrder.isEmpty()) {
            Object oldest = registrationOrder.removeFirst();
            pending.remove(oldest);
            bound.remove(oldest);
        }
    }

    private static boolean sameParticleOrigin(
            SubmissionContext point,
            SubmissionContext batch) {
        return point.levelIdentity() == batch.levelIdentity()
                && point.entityIdentity() == batch.entityIdentity()
                && point.ownerUuid().equals(batch.ownerUuid())
                && point.entityUuid().equals(batch.entityUuid())
                && point.entityRuntimeId() == batch.entityRuntimeId()
                && point.dimension().equals(batch.dimension())
                && point.sessionEpoch() == batch.sessionEpoch()
                && point.sceneEpoch() == batch.sceneEpoch()
                && point.sceneId().equals(batch.sceneId())
                && point.resource().equals(batch.resource())
                && point.gameTime() <= batch.gameTime();
    }

    record Ticket(Object identity, long id) {}

    record PointClaim(
            Ticket ticket,
            SubmissionContext context,
            ParticlePointEvidence evidence) {}

    record SubmissionContext(
            UUID ownerUuid,
            UUID entityUuid,
            int entityRuntimeId,
            Object entityIdentity,
            Object levelIdentity,
            String dimension,
            long sessionEpoch,
            long sceneEpoch,
            String sceneId,
            long gameTime,
            long frameNanos,
            String receiptType,
            String resource,
            String evidenceDigest) {
        SubmissionContext {
            Objects.requireNonNull(ownerUuid);
            Objects.requireNonNull(entityUuid);
            Objects.requireNonNull(entityIdentity);
            Objects.requireNonNull(levelIdentity);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(sceneId);
            Objects.requireNonNull(receiptType);
            Objects.requireNonNull(resource);
            Objects.requireNonNull(evidenceDigest);
        }

        boolean matches(SubmissionContext other) {
            return other != null
                    && ownerUuid.equals(other.ownerUuid)
                    && entityUuid.equals(other.entityUuid)
                    && entityRuntimeId == other.entityRuntimeId
                    && entityIdentity == other.entityIdentity
                    && levelIdentity == other.levelIdentity
                    && dimension.equals(other.dimension)
                    && sessionEpoch == other.sessionEpoch
                    && sceneEpoch == other.sceneEpoch
                    && sceneId.equals(other.sceneId)
                    && gameTime == other.gameTime
                    && frameNanos == other.frameNanos
                    && receiptType.equals(other.receiptType)
                    && resource.equals(other.resource)
                    && evidenceDigest.equals(other.evidenceDigest);
        }
    }

    sealed interface SubmissionEvidence permits GeometryEvidence,
            ParticlePointEvidence, ParticleBatchEvidence {
        String canonical();

        default String digest() {
            return sha256(canonical());
        }
    }

    record GeometryEvidence(
            int vertexCount,
            double submittedMinX,
            double submittedMinY,
            double submittedMinZ,
            double submittedMaxX,
            double submittedMaxY,
            double submittedMaxZ,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ) implements SubmissionEvidence {
        @Override
        public String canonical() {
            return "geometry|" + vertexCount
                    + "|" + bits(submittedMinX)
                    + "|" + bits(submittedMinY)
                    + "|" + bits(submittedMinZ)
                    + "|" + bits(submittedMaxX)
                    + "|" + bits(submittedMaxY)
                    + "|" + bits(submittedMaxZ)
                    + "|" + bits(startX)
                    + "|" + bits(startY)
                    + "|" + bits(startZ)
                    + "|" + bits(endX)
                    + "|" + bits(endY)
                    + "|" + bits(endZ);
        }
    }

    record ParticlePointEvidence(
            long sequence,
            String resource,
            long gameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) implements SubmissionEvidence {
        ParticlePointEvidence {
            Objects.requireNonNull(resource);
        }

        @Override
        public String canonical() {
            return "point|" + sequence
                    + "|" + resource
                    + "|" + gameTime
                    + "|" + bits(x)
                    + "|" + bits(y)
                    + "|" + bits(z)
                    + "|" + bits(velocityX)
                    + "|" + bits(velocityY)
                    + "|" + bits(velocityZ);
        }
    }

    record ParticleBatchEvidence(
            String resource,
            List<ParticlePointEvidence> points)
            implements SubmissionEvidence {
        ParticleBatchEvidence {
            Objects.requireNonNull(resource);
            points = List.copyOf(points);
        }

        @Override
        public String canonical() {
            StringBuilder value = new StringBuilder("batch|")
                    .append(resource)
                    .append('|')
                    .append(points.size());
            for (ParticlePointEvidence point : points) {
                value.append('|').append(point.canonical());
            }
            return value.toString();
        }
    }

    private record PendingRegistration(
            long ticketId,
            SubmissionContext context,
            SubmissionEvidence evidence) {}

    private record BoundRegistration(
            long ticketId,
            SubmissionContext context,
            SubmissionEvidence evidence,
            Object receiptFingerprint) {}

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

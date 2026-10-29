package rotp.addons.ultimate_lifeform.client;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.GeometryEvidence;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.Operation;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.ParticleBatchEvidence;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.ParticlePointEvidence;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.PointClaim;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.SubmissionContext;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.SubmissionEvidence;
import rotp.addons.ultimate_lifeform.client.DownstreamTicketAuthority.Ticket;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.DownstreamPointTicket;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.EmissionBatch;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.EmissionPoint;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;

import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

public final class UltimateLifeformDownstreamSubmissions {
    private static final int MAX_TICKETS = 32768;
    private static final StackWalker CALLER_WALKER = StackWalker.getInstance(
            Set.of(
                    StackWalker.Option.RETAIN_CLASS_REFERENCE,
                    StackWalker.Option.SHOW_REFLECT_FRAMES,
                    StackWalker.Option.SHOW_HIDDEN_FRAMES));
    private static final DownstreamTicketAuthority AUTHORITY =
            new DownstreamTicketAuthority(
                    UltimateLifeformDownstreamSubmissions::callerAllowed,
                    MAX_TICKETS);
    @Nullable private static ClientLevel activeLevel;
    @Nullable private static UUID activeOwnerUuid;
    private static String activeDimension = "none";
    private static String activeSceneId = "none";
    private static long sessionEpoch;
    private static long sceneEpoch;
    private static boolean sceneOpen;

    private UltimateLifeformDownstreamSubmissions() {}

    public static synchronized long beginScene(
            ClientLevel level, UUID ownerUuid, String sceneId) {
        if (level == null || ownerUuid == null
                || sceneId == null || sceneId.isBlank()) {
            return -1L;
        }
        String dimension = dimension(level);
        if (activeLevel != level
                || !activeDimension.equals(dimension)) {
            transitionLevel(level, dimension);
        }
        clearTickets();
        activeOwnerUuid = ownerUuid;
        activeSceneId = sceneId;
        sceneEpoch = nextEpoch(sceneEpoch);
        sceneOpen = true;
        return sceneEpoch;
    }

    public static synchronized boolean endScene(long expectedSceneEpoch) {
        if (!sceneOpen || sceneEpoch != expectedSceneEpoch) {
            clearTickets();
            sceneOpen = false;
            activeOwnerUuid = null;
            activeSceneId = "none";
            return false;
        }
        clearTickets();
        sceneOpen = false;
        activeOwnerUuid = null;
        activeSceneId = "none";
        sceneEpoch = nextEpoch(sceneEpoch);
        return true;
    }

    public static synchronized void observeClientLevel(
            @Nullable ClientLevel level) {
        if (level == null) {
            if (activeLevel != null || sceneOpen) {
                clearTickets();
                activeLevel = null;
                activeDimension = "none";
                activeOwnerUuid = null;
                activeSceneId = "none";
                sceneOpen = false;
                sessionEpoch = nextEpoch(sessionEpoch);
                sceneEpoch = nextEpoch(sceneEpoch);
            }
            return;
        }
        String dimension = dimension(level);
        if (activeLevel != level
                || !activeDimension.equals(dimension)) {
            transitionLevel(level, dimension);
        }
    }

    @Nullable
    static synchronized RegisteredSubmission registerGeometry(
            Entity entity,
            ClientReceipt receipt,
            String resource,
            GeometryEvidence evidence,
            long frameNanos) {
        SubmissionContext context = context(
                entity,
                receipt.name(),
                resource,
                evidence,
                entity.level().getGameTime(),
                frameNanos);
        if (context == null) {
            return null;
        }
        Ticket ticket = AUTHORITY.register(
                Operation.REGISTER_GEOMETRY, context, evidence);
        return ticket != null
                ? new RegisteredSubmission(ticket, context, evidence)
                : null;
    }

    @Nullable
    static synchronized DownstreamPointTicket registerParticlePoint(
            UltimateAreaEffectEntity entity,
            ClientLevel level,
            long sequence,
            String resource,
            long gameTime,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        if (entity == null || entity.level() != level
                || gameTime != level.getGameTime()) {
            return null;
        }
        ParticlePointEvidence evidence = new ParticlePointEvidence(
                sequence,
                resource,
                gameTime,
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ);
        SubmissionContext context = context(
                entity,
                "PARTICLE_POINT",
                resource,
                evidence,
                gameTime,
                Util.getNanos());
        if (context == null) {
            return null;
        }
        Ticket ticket = AUTHORITY.register(
                Operation.REGISTER_PARTICLE_POINT, context, evidence);
        return ticket != null
                ? pointTicket(ticket, context)
                : null;
    }

    @Nullable
    static synchronized RegisteredSubmission combineParticleBatch(
            UltimateAreaEffectEntity entity,
            ClientReceipt receipt,
            EmissionBatch batch) {
        if (entity == null || batch == null
                || batch.count() <= 0
                || batch.points().size() != batch.count()) {
            discardPointTickets(batch);
            return null;
        }
        List<ParticlePointEvidence> pointEvidence = batch.points().stream()
                .map(UltimateLifeformDownstreamSubmissions::pointEvidence)
                .toList();
        ParticleBatchEvidence evidence = new ParticleBatchEvidence(
                batch.resource(), pointEvidence);
        SubmissionContext context = context(
                entity,
                receipt.name(),
                batch.resource(),
                evidence,
                entity.level().getGameTime(),
                Util.getNanos());
        if (context == null) {
            discardPointTickets(batch);
            return null;
        }
        List<PointClaim> claims = batch.points().stream()
                .map(point -> new PointClaim(
                        ticket(point.downstreamTicket()),
                        pointContext(point),
                        pointEvidence(point)))
                .toList();
        Ticket batchTicket = AUTHORITY.combineParticleBatch(
                context, evidence, claims);
        return batchTicket != null
                ? new RegisteredSubmission(batchTicket, context, evidence)
                : null;
    }

    static synchronized void discardParticleBatch(
            @Nullable EmissionBatch batch) {
        discardPointTickets(batch);
    }

    static synchronized void discardParticlePoint(
            @Nullable DownstreamPointTicket pointTicket) {
        AUTHORITY.discard(ticket(pointTicket));
    }

    static synchronized void discardRegistration(
            @Nullable RegisteredSubmission registration) {
        if (registration != null) {
            AUTHORITY.discard(registration.ticket());
        }
    }

    static synchronized void discardReceiptTicket(
            @Nullable Object ticketIdentity, long ticketId) {
        if (ticketIdentity != null) {
            AUTHORITY.discard(new Ticket(ticketIdentity, ticketId));
        }
    }

    static synchronized boolean bindReceipt(
            RegisteredSubmission registration,
            Object receiptFingerprint) {
        return registration != null && AUTHORITY.bind(
                registration.ticket(),
                registration.context(),
                registration.evidence(),
                receiptFingerprint);
    }

    @Nullable
    static synchronized String consumeReceipt(
            @Nullable Object ticketIdentity,
            long ticketId,
            @Nullable SubmissionContext expectedContext,
            Object receiptFingerprint) {
        return AUTHORITY.consume(
                new Ticket(ticketIdentity, ticketId),
                expectedContext,
                receiptFingerprint);
    }

    @Nullable
    static synchronized SubmissionContext validationContext(
            @Nullable ClientLevel level,
            @Nullable Entity entity,
            UUID expectedOwnerUuid,
            String receiptType,
            String resource,
            String evidenceDigest,
            long expectedSessionEpoch,
            long expectedSceneEpoch,
            String expectedSceneId,
            long gameTime,
            long frameNanos) {
        if (level == null
                || entity == null
                || !sceneOpen
                || activeLevel != level
                || entity.level() != level
                || sessionEpoch != expectedSessionEpoch
                || sceneEpoch != expectedSceneEpoch
                || !activeSceneId.equals(expectedSceneId)
                || activeOwnerUuid == null
                || !activeOwnerUuid.equals(expectedOwnerUuid)
                || !dimension(level).equals(activeDimension)) {
            return null;
        }
        UUID actualOwner = ownerUuid(entity);
        if (actualOwner == null || !actualOwner.equals(expectedOwnerUuid)) {
            return null;
        }
        return new SubmissionContext(
                actualOwner,
                entity.getUUID(),
                entity.getId(),
                entity,
                level,
                activeDimension,
                sessionEpoch,
                sceneEpoch,
                activeSceneId,
                gameTime,
                frameNanos,
                receiptType,
                resource,
                evidenceDigest);
    }

    static synchronized long currentSessionEpoch() {
        return sessionEpoch;
    }

    static synchronized long currentSceneEpoch() {
        return sceneOpen ? sceneEpoch : -1L;
    }

    static synchronized String currentSceneId() {
        return sceneOpen ? activeSceneId : "none";
    }

    private static SubmissionContext context(
            Entity entity,
            String receiptType,
            String resource,
            SubmissionEvidence evidence,
            long gameTime,
            long frameNanos) {
        if (!(entity.level() instanceof ClientLevel level)) {
            return null;
        }
        observeClientLevel(level);
        UUID ownerUuid = ownerUuid(entity);
        if (!sceneOpen
                || activeLevel != level
                || activeOwnerUuid == null
                || ownerUuid == null
                || !activeOwnerUuid.equals(ownerUuid)) {
            return null;
        }
        return new SubmissionContext(
                ownerUuid,
                entity.getUUID(),
                entity.getId(),
                entity,
                level,
                activeDimension,
                sessionEpoch,
                sceneEpoch,
                activeSceneId,
                gameTime,
                frameNanos,
                receiptType,
                resource,
                evidence.digest());
    }

    @Nullable
    private static UUID ownerUuid(Entity entity) {
        if (entity instanceof UltimateScaleProjectileEntity scale) {
            Entity owner = scale.getOwner();
            return owner != null ? owner.getUUID() : null;
        }
        Entity owner = entity instanceof UltimateProjectileEntity projectile
                ? projectile.getOwner()
                : entity instanceof UltimateAreaEffectEntity area
                        ? area.getOwner()
                        : null;
        return owner != null ? owner.getUUID() : null;
    }

    private static DownstreamPointTicket pointTicket(
            Ticket ticket, SubmissionContext context) {
        return new DownstreamPointTicket(
                ticket.identity(),
                ticket.id(),
                context.ownerUuid(),
                context.entityUuid(),
                context.entityRuntimeId(),
                context.entityIdentity(),
                context.levelIdentity(),
                context.dimension(),
                context.sessionEpoch(),
                context.sceneEpoch(),
                context.sceneId(),
                context.frameNanos(),
                context.evidenceDigest());
    }

    private static Ticket ticket(DownstreamPointTicket ticket) {
        return ticket != null
                ? new Ticket(ticket.identity(), ticket.id())
                : null;
    }

    private static SubmissionContext pointContext(EmissionPoint point) {
        DownstreamPointTicket ticket = point.downstreamTicket();
        return ticket == null ? null : new SubmissionContext(
                ticket.ownerUuid(),
                ticket.entityUuid(),
                ticket.entityRuntimeId(),
                ticket.entityIdentity(),
                ticket.levelIdentity(),
                ticket.dimension(),
                ticket.sessionEpoch(),
                ticket.sceneEpoch(),
                ticket.sceneId(),
                point.clientGameTime(),
                ticket.frameNanos(),
                "PARTICLE_POINT",
                point.resource(),
                ticket.evidenceDigest());
    }

    private static ParticlePointEvidence pointEvidence(
            EmissionPoint point) {
        return new ParticlePointEvidence(
                point.sequence(),
                point.resource(),
                point.clientGameTime(),
                point.x(),
                point.y(),
                point.z(),
                point.velocityX(),
                point.velocityY(),
                point.velocityZ());
    }

    private static void discardPointTickets(
            @Nullable EmissionBatch batch) {
        if (batch == null) {
            return;
        }
        ParticleBatchEvidence empty = new ParticleBatchEvidence(
                batch.resource(), List.of());
        AUTHORITY.combineParticleBatch(
                invalidContext(empty),
                empty,
                batch.points().stream()
                        .map(point -> new PointClaim(
                                ticket(point.downstreamTicket()),
                                pointContext(point),
                                pointEvidence(point)))
                        .toList());
    }

    private static SubmissionContext invalidContext(
            SubmissionEvidence evidence) {
        Object invalid = new Object();
        return new SubmissionContext(
                new UUID(0L, 0L),
                new UUID(0L, 0L),
                -1,
                invalid,
                invalid,
                "none",
                -1L,
                -1L,
                "none",
                -1L,
                -1L,
                "none",
                "none",
                evidence.digest());
    }

    private static void transitionLevel(
            ClientLevel level, String dimension) {
        clearTickets();
        activeLevel = level;
        activeDimension = dimension;
        activeOwnerUuid = null;
        activeSceneId = "none";
        sceneOpen = false;
        sessionEpoch = nextEpoch(sessionEpoch);
        sceneEpoch = nextEpoch(sceneEpoch);
    }

    private static void clearTickets() {
        AUTHORITY.clear();
        UltimateParticleEmissionEvidence.clearPendingDownstream();
    }

    private static String dimension(ClientLevel level) {
        return level.dimension().location().toString();
    }

    private static long nextEpoch(long current) {
        return current == Long.MAX_VALUE ? 1L : current + 1L;
    }

    private static boolean callerAllowed(Operation operation) {
        List<StackFrame> frames = CALLER_WALKER.walk(
                stream -> stream.limit(64).toList());
        return frames.stream().limit(12).noneMatch(
                UltimateLifeformDownstreamSubmissions::reflectiveFrame);
    }

    private static boolean geometryBindCaller(List<StackFrame> frames) {
        return ordered(
                frames,
                frame(UltimateLifeformDownstreamSubmissions.class,
                        "bindReceipt"),
                frame(UltimateLifeformClientAcceptance.class,
                        "recordGlobalGeometrySubmission"));
    }

    private static boolean particleBindCaller(List<StackFrame> frames) {
        return consecutive(
                frames,
                frame(UltimateLifeformDownstreamSubmissions.class,
                        "bindReceipt"),
                frame(UltimateLifeformClientAcceptance.class, "record"),
                frame(UltimateLifeformClientAcceptance.class,
                        "recordGlobalParticleEmission"),
                frame(UltimateAreaEffectRenderer.class, "render"));
    }

    private static boolean reflectiveFrame(StackFrame frame) {
        String name = frame.getClassName();
        return name.startsWith("java.lang.reflect.")
                || name.startsWith("jdk.internal.reflect.")
                || name.startsWith("java.lang.invoke.");
    }

    private static boolean ordered(
            List<StackFrame> frames, FrameMatch... expected) {
        int cursor = 0;
        for (FrameMatch match : expected) {
            boolean found = false;
            while (cursor < frames.size()) {
                if (match.matches(frames.get(cursor++))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean consecutive(
            List<StackFrame> frames, FrameMatch... expected) {
        for (int start = 0;
                start + expected.length <= frames.size();
                start++) {
            boolean matched = true;
            for (int offset = 0; offset < expected.length; offset++) {
                if (!expected[offset].matches(frames.get(start + offset))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static FrameMatch frame(
            Class<?> type, @Nullable String method) {
        return frame -> frame.getDeclaringClass() == type
                && (method == null
                        || frame.getMethodName().equals(method));
    }

    private static FrameMatch frameName(
            String className, @Nullable String method) {
        return frame -> frame.getClassName().equals(className)
                && frame.getDeclaringClass().getClassLoader()
                        == UltimateLifeformDownstreamSubmissions.class
                                .getClassLoader()
                && (method == null || frame.getMethodName().equals(method));
    }

    private static FrameMatch frameAny(
            Class<?> type, String... methods) {
        Set<String> allowedMethods = Set.of(methods);
        return frame -> frame.getDeclaringClass() == type
                && allowedMethods.contains(frame.getMethodName());
    }

    @FunctionalInterface
    private interface FrameMatch {
        boolean matches(StackFrame frame);
    }

    record RegisteredSubmission(
            Ticket ticket,
            SubmissionContext context,
            SubmissionEvidence evidence) {}
}

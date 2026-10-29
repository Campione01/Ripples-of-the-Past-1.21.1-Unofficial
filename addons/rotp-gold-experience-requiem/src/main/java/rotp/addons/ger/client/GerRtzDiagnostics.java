package rotp.addons.ger.client;

import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Read-only RTZ render and audio receipts for runtime validation.
 */
@OnlyIn(Dist.CLIENT)
public final class GerRtzDiagnostics {
    private static long presentationSequence;
    private static boolean active;
    private static int payloadMode = -1;
    private static Mode effectiveMode = Mode.OFF;
    private static int elapsedTicks;
    private static float lifecycleAlpha;
    private static boolean ownerResolved;
    private static boolean sourceResolved;
    private static int trailEntityCount;
    private static int trailPointCount;
    private static long startedFrameNanos = -1L;
    private static long loopSoundPlayRequestCount;
    private static long loopSoundPlaybackCount;
    private static long loopSoundPlaybackTotalCount;
    private static String loopSoundPlaybackSoundId = "not_observed";
    private static String loopSoundPlaybackSoundClass = "not_observed";
    private static SoundInstance trackedLoopSound;
    private static long loopSoundStopReceiptCount;

    private static long auraDrawCount;
    private static int auraLastBatchCount;
    private static long auraLastFrameNanos = -1L;
    private static long fogApplyCount;
    private static int fogLastBatchCount;
    private static long fogLastFrameNanos = -1L;
    private static long stormPrimitiveCount;
    private static int stormLastBatchCount;
    private static long stormLastFrameNanos = -1L;
    private static long postProcessCount;
    private static int postLastBatchCount;
    private static long postLastFrameNanos = -1L;
    private static long ghostDrawCount;
    private static int ghostLastBatchCount;
    private static long ghostLastFrameNanos = -1L;
    private static long ghostRenderStateCheckCount;
    private static long ghostRenderStateMismatchCount;
    private static RenderTargetSnapshot ghostBeforeRenderState =
            RenderTargetSnapshot.unavailable();
    private static RenderTargetSnapshot ghostAfterRenderState =
            RenderTargetSnapshot.unavailable();

    private GerRtzDiagnostics() {}

    static synchronized void begin(
            StartPayload payload, Mode mode) {
        presentationSequence++;
        active = true;
        payloadMode = payload.mode();
        effectiveMode = mode;
        elapsedTicks = payload.elapsedTicks();
        lifecycleAlpha = 0.0F;
        ownerResolved = false;
        sourceResolved = false;
        trailEntityCount = payload.trails().size();
        trailPointCount = payload.trails().stream()
                .mapToInt(trail -> trail.points().size())
                .sum();
        startedFrameNanos = frameNanos();
        loopSoundPlayRequestCount = 0L;
        loopSoundPlaybackCount = 0L;
        loopSoundPlaybackSoundId = "not_observed";
        loopSoundPlaybackSoundClass = "not_observed";
        trackedLoopSound = null;
        loopSoundStopReceiptCount = 0L;
        auraDrawCount = 0L;
        auraLastBatchCount = 0;
        auraLastFrameNanos = -1L;
        fogApplyCount = 0L;
        fogLastBatchCount = 0;
        fogLastFrameNanos = -1L;
        stormPrimitiveCount = 0L;
        stormLastBatchCount = 0;
        stormLastFrameNanos = -1L;
        postProcessCount = 0L;
        postLastBatchCount = 0;
        postLastFrameNanos = -1L;
        ghostDrawCount = 0L;
        ghostLastBatchCount = 0;
        ghostLastFrameNanos = -1L;
        ghostRenderStateCheckCount = 0L;
        ghostRenderStateMismatchCount = 0L;
        ghostBeforeRenderState =
                RenderTargetSnapshot.unavailable();
        ghostAfterRenderState =
                RenderTargetSnapshot.unavailable();
    }

    static synchronized void updateState(
            int elapsed,
            float alpha,
            boolean ownerPresent,
            boolean sourcePresent) {
        elapsedTicks = elapsed;
        lifecycleAlpha = alpha;
        ownerResolved = ownerPresent;
        sourceResolved = sourcePresent;
    }

    static synchronized void end() {
        active = false;
        lifecycleAlpha = 0.0F;
        ownerResolved = false;
        sourceResolved = false;
    }

    static synchronized void recordLoopSoundPlayRequest() {
        if (active) {
            loopSoundPlayRequestCount++;
        }
    }

    static synchronized void recordLoopSoundPlayback(
            SoundInstance sound) {
        if (!active || sound == null) {
            return;
        }
        loopSoundPlaybackCount++;
        loopSoundPlaybackTotalCount++;
        loopSoundPlaybackSoundId = sound.getLocation().toString();
        loopSoundPlaybackSoundClass = sound.getClass().getName();
        trackedLoopSound = sound;
    }

    public static synchronized long loopSoundPlaybackTotalCount() {
        return loopSoundPlaybackTotalCount;
    }

    static synchronized void recordLoopSoundStopReceipt() {
        if (active) {
            loopSoundStopReceiptCount++;
        }
    }

    static synchronized void recordAuraDraw(int targets) {
        record(targets, ReceiptKind.AURA);
    }

    static synchronized void recordFogApply() {
        record(1, ReceiptKind.FOG);
    }

    static synchronized void recordStormPrimitives(
            int primitives) {
        record(primitives, ReceiptKind.STORM);
    }

    static synchronized void recordPostProcess() {
        record(1, ReceiptKind.POST);
    }

    static synchronized void recordGhostDraw(int ghosts) {
        record(ghosts, ReceiptKind.GHOST);
    }

    static synchronized void recordGhostRenderState(
            RenderTargetSnapshot before,
            RenderTargetSnapshot after) {
        if (!active) {
            return;
        }
        ghostRenderStateCheckCount++;
        if (!before.equals(after)) {
            ghostRenderStateMismatchCount++;
        }
        ghostBeforeRenderState = before;
        ghostAfterRenderState = after;
    }

    public static synchronized Snapshot snapshot() {
        boolean trackedLoopSoundPresent = trackedLoopSound != null;
        boolean trackedLoopSoundActive = trackedLoopSoundPresent
                && Minecraft.getInstance().getSoundManager()
                        .isActive(trackedLoopSound);
        return new Snapshot(
                presentationSequence,
                active,
                payloadMode,
                effectiveMode,
                elapsedTicks,
                lifecycleAlpha,
                ownerResolved,
                sourceResolved,
                trailEntityCount,
                trailPointCount,
                startedFrameNanos,
                loopSoundPlayRequestCount,
                loopSoundPlaybackCount,
                loopSoundPlaybackTotalCount,
                loopSoundPlaybackSoundId,
                loopSoundPlaybackSoundClass,
                trackedLoopSoundPresent,
                trackedLoopSoundActive,
                loopSoundStopReceiptCount,
                auraDrawCount,
                auraLastBatchCount,
                auraLastFrameNanos,
                fogApplyCount,
                fogLastBatchCount,
                fogLastFrameNanos,
                stormPrimitiveCount,
                stormLastBatchCount,
                stormLastFrameNanos,
                postProcessCount,
                postLastBatchCount,
                postLastFrameNanos,
                ghostDrawCount,
                ghostLastBatchCount,
                ghostLastFrameNanos,
                ghostRenderStateCheckCount,
                ghostRenderStateMismatchCount,
                ghostBeforeRenderState,
                ghostAfterRenderState);
    }

    private static void record(
            int batchCount, ReceiptKind kind) {
        if (!active || batchCount <= 0) {
            return;
        }
        long frame = frameNanos();
        switch (kind) {
            case AURA -> {
                auraDrawCount += batchCount;
                auraLastBatchCount = batchCount;
                auraLastFrameNanos = frame;
            }
            case FOG -> {
                fogApplyCount += batchCount;
                fogLastBatchCount = batchCount;
                fogLastFrameNanos = frame;
            }
            case STORM -> {
                stormPrimitiveCount += batchCount;
                stormLastBatchCount = batchCount;
                stormLastFrameNanos = frame;
            }
            case POST -> {
                postProcessCount += batchCount;
                postLastBatchCount = batchCount;
                postLastFrameNanos = frame;
            }
            case GHOST -> {
                ghostDrawCount += batchCount;
                ghostLastBatchCount = batchCount;
                ghostLastFrameNanos = frame;
            }
        }
    }

    private static long frameNanos() {
        return Util.getNanos();
    }

    private enum ReceiptKind {
        AURA,
        FOG,
        STORM,
        POST,
        GHOST
    }

    public record Snapshot(
            long presentationSequence,
            boolean active,
            int payloadMode,
            Mode effectiveMode,
            int elapsedTicks,
            float lifecycleAlpha,
            boolean ownerResolved,
            boolean sourceResolved,
            int trailEntityCount,
            int trailPointCount,
            long startedFrameNanos,
            long loopSoundPlayRequestCount,
            long loopSoundPlaybackCount,
            long loopSoundPlaybackTotalCount,
            String loopSoundPlaybackSoundId,
            String loopSoundPlaybackSoundClass,
            boolean loopSoundTrackedInstancePresent,
            boolean loopSoundTrackedInstanceActive,
            long loopSoundStopReceiptCount,
            long auraDrawCount,
            int auraLastBatchCount,
            long auraLastFrameNanos,
            long fogApplyCount,
            int fogLastBatchCount,
            long fogLastFrameNanos,
            long stormPrimitiveCount,
            int stormLastBatchCount,
            long stormLastFrameNanos,
            long postProcessCount,
            int postLastBatchCount,
            long postLastFrameNanos,
            long ghostDrawCount,
            int ghostLastBatchCount,
            long ghostLastFrameNanos,
            long ghostRenderStateCheckCount,
            long ghostRenderStateMismatchCount,
            RenderTargetSnapshot ghostBeforeRenderState,
            RenderTargetSnapshot ghostAfterRenderState) {
        public boolean ghostRenderStateRestored() {
            return ghostRenderStateCheckCount > 0L
                    && ghostRenderStateMismatchCount == 0L;
        }
    }

    public record RenderTargetSnapshot(
            int drawFramebuffer,
            int readFramebuffer,
            int viewportX,
            int viewportY,
            int viewportWidth,
            int viewportHeight) {
        private static RenderTargetSnapshot unavailable() {
            return new RenderTargetSnapshot(
                    -1, -1, -1, -1, -1, -1);
        }
    }
}

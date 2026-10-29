package rotp.addons.whitealbum.client;

import java.util.UUID;

import rotp.core.client.entityrender.stand.StandVisualContext;
import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Acceptance-only receipts emitted after the production wearable model has
 * submitted its vertices. Resource bytes and displayed-frame pixels remain
 * separate evidence owned by the client profile harness.
 */
public final class WhiteAlbumRenderDiagnostics {
    public static final String SUIT_DEFAULT_DONOR_SOURCE_SHA256 =
            "4B4D5BD5EC8D414FA2A363B217CA0389A2C886CFDFE9F8122E02D6A0E3F89A19";
    public static final String SUIT_SLIM_DONOR_SOURCE_SHA256 =
            "5815D371C160284412059EBF8E54BB0E3162EAD673890755CCB9655E12CC27F1";
    public static final String SUIT_DEFAULT_RESOURCE_SHA256 =
            "3F57186E1C556D4D2B16385E69F3B8B087F2363402023ADB0CE7624A4351B25C";
    public static final String SUIT_SLIM_RESOURCE_SHA256 =
            "DF4A2311B69832A164E409263F84EA7B5128EC30EA0919666C3CCC3323E50F4D";

    private static volatile boolean enabled;
    private static UUID expectedPlayerUuid;
    private static long sequence;
    private static long baselineSequence;
    private static Snapshot latest = Snapshot.empty();

    private WhiteAlbumRenderDiagnostics() {}

    public static synchronized void begin(UUID playerUuid) {
        enabled = true;
        expectedPlayerUuid = playerUuid;
        baselineSequence = sequence;
        latest = Snapshot.awaiting(sequence, playerUuid);
    }

    public static synchronized void end() {
        enabled = false;
        expectedPlayerUuid = null;
    }

    public static synchronized boolean observing(UUID playerUuid) {
        return enabled && playerUuid != null
                && playerUuid.equals(expectedPlayerUuid);
    }

    public static synchronized Snapshot snapshot() {
        return latest;
    }

    static void recordLayerDraw(
            LivingEntity player,
            WhiteAlbumControllerEntity controller,
            WhiteAlbumWearableModel<?> model,
            ResourceLocation texture,
            StandVisualContext context) {
        if (!enabled
                || model.donorLayer()
                        != WhiteAlbumWearableModel.DonorLayer.SUIT) {
            return;
        }
        synchronized (WhiteAlbumRenderDiagnostics.class) {
            if (!enabled
                    || expectedPlayerUuid == null
                    || !expectedPlayerUuid.equals(player.getUUID())) {
                return;
            }
            boolean slim = model.slim();
            ResourceLocation expectedTexture = slim
                    ? WhiteAlbumVisuals.SUIT_SLIM
                    : WhiteAlbumVisuals.SUIT_DEFAULT;
            WhiteAlbumWearableModel.DonorGeometryProof geometry =
                    model.donorGeometryProof();
            long drawSequence = ++sequence;
            latest = new Snapshot(
                    drawSequence,
                    drawSequence - baselineSequence,
                    System.nanoTime(),
                    RenderSystem.isOnRenderThread(),
                    player.getUUID(),
                    controller.getUUID(),
                    geometry.donorLayer(),
                    slim,
                    geometry.inflation(),
                    geometry.textureWidth(),
                    geometry.textureHeight(),
                    geometry.partCount(),
                    geometry.cubeCount(),
                    geometry.matched(),
                    geometry.actualSha256(),
                    geometry.expectedSha256(),
                    geometry.mismatch(),
                    texture.toString(),
                    expectedTexture.toString(),
                    texture.equals(expectedTexture),
                    slim
                            ? SUIT_SLIM_DONOR_SOURCE_SHA256
                            : SUIT_DEFAULT_DONOR_SOURCE_SHA256,
                    slim
                            ? SUIT_SLIM_RESOURCE_SHA256
                            : SUIT_DEFAULT_RESOURCE_SHA256,
                    context.effectiveAlpha());
        }
    }

    public record Snapshot(
            long sequence,
            long drawCountSinceBegin,
            long renderNanos,
            boolean renderThread,
            UUID playerUuid,
            UUID controllerUuid,
            String donorLayer,
            boolean slim,
            float inflation,
            int logicalTextureWidth,
            int logicalTextureHeight,
            int partCount,
            int cubeCount,
            boolean donorGeometryMatched,
            String actualGeometrySha256,
            String expectedGeometrySha256,
            String geometryMismatch,
            String textureResource,
            String expectedTextureResource,
            boolean donorTexturePathMatched,
            String donorTextureSourceSha256,
            String expectedRuntimeResourceSha256,
            float effectiveAlpha) {
        private static Snapshot empty() {
            return awaiting(0L, null);
        }

        private static Snapshot awaiting(
                long sequence,
                UUID playerUuid) {
            return new Snapshot(
                    sequence,
                    0L,
                    -1L,
                    false,
                    playerUuid,
                    null,
                    "not_observed",
                    false,
                    -1.0F,
                    -1,
                    -1,
                    -1,
                    -1,
                    false,
                    "not_observed",
                    "not_observed",
                    "draw_not_observed",
                    "not_observed",
                    "not_observed",
                    false,
                    "not_observed",
                    "not_observed",
                    -1.0F);
        }
    }
}

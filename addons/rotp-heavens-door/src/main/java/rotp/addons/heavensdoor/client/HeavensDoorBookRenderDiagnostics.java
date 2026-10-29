package rotp.addons.heavensdoor.client;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;
import rotp.addons.heavensdoor.state.BookState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Passive receipts emitted only after production book draws complete. */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(
        modid = HeavensDoorAddon.MOD_ID,
        value = Dist.CLIENT)
public final class HeavensDoorBookRenderDiagnostics {
    private static final int MAX_RECEIPTS = 256;
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;
    private static final Deque<StoredScreenReceipt> SCREEN_RECEIPTS =
            new ArrayDeque<>();
    private static final Deque<OverlayDrawReceipt> OVERLAY_RECEIPTS =
            new ArrayDeque<>();
    private static long sequence;
    private static long clientFrameId;

    private HeavensDoorBookRenderDiagnostics() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static synchronized void onRenderFramePre(
            RenderFrameEvent.Pre event) {
        clientFrameId++;
    }

    static synchronized void recordScreenDraw(
            HeavensDoorBookScreen screen,
            OpenBookSnapshotPayload snapshot,
            int bookLeft,
            int bookTop,
            int guiWidth,
            int guiHeight,
            ResourceLocation texture,
            int pageColor,
            int inkColor,
            int separatorColor,
            HeavensDoorBookLayout.Area leftPage,
            HeavensDoorBookLayout.Area rightPage,
            List<TextFieldReceipt> textFields,
            int renderableCount) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level != null
                ? minecraft.level.getEntity(snapshot.entityId())
                : null;
        if (!(entity instanceof LivingEntity target)) {
            return;
        }
        ScreenDrawReceipt receipt = new ScreenDrawReceipt(
                nextSequence(),
                clientFrameId,
                System.identityHashCode(screen),
                target.getUUID(),
                target.getId(),
                snapshot.sessionNonce(),
                snapshot.sessionGeneration(),
                snapshot.sessionExpiresAt(),
                texture.toString(),
                bookLeft,
                bookTop,
                HeavensDoorBookLayout.IMAGE_WIDTH,
                HeavensDoorBookLayout.IMAGE_HEIGHT,
                guiWidth,
                guiHeight,
                pageColor,
                inkColor,
                separatorColor,
                leftPage,
                rightPage,
                textFields,
                renderableCount,
                snapshot.availableCommandMask());
        SCREEN_RECEIPTS.addLast(new StoredScreenReceipt(
                new WeakReference<>(screen), receipt));
        trim(SCREEN_RECEIPTS);
    }

    static synchronized void recordOverlayDraw(
            LivingEntity target,
            LivingEntity renderedEntity,
            BookState state,
            int stage,
            ResourceLocation baseTexture,
            ResourceLocation overlayTexture,
            int textureWidth,
            int textureHeight,
            String rendererClass,
            String modelClass,
            int packedLight,
            int packedOverlay,
            float poseScale,
            int color,
            float effectiveAlpha) {
        UUID ownerUuid = state.ownerUuid();
        ResourceLocation dimension = state.dimension();
        if (!state.active()
                || ownerUuid == null
                || dimension == null) {
            return;
        }
        OVERLAY_RECEIPTS.addLast(new OverlayDrawReceipt(
                nextSequence(),
                clientFrameId,
                target.getUUID(),
                target.getId(),
                renderedEntity.getUUID(),
                renderedEntity.getId(),
                ownerUuid,
                dimension.toString(),
                state.generation(),
                state.bookAmplifier(),
                stage,
                baseTexture.toString(),
                overlayTexture.toString(),
                textureWidth,
                textureHeight,
                Objects.requireNonNull(rendererClass),
                Objects.requireNonNull(modelClass),
                packedLight,
                packedOverlay,
                poseScale,
                color,
                effectiveAlpha));
        trim(OVERLAY_RECEIPTS);
    }

    static long fingerprint(String value) {
        long hash = FNV_OFFSET_BASIS;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public static synchronized long latestSequence() {
        return sequence;
    }

    public static synchronized long currentFrameId() {
        return clientFrameId;
    }

    @Nullable
    public static synchronized ScreenDrawReceipt latestScreenReceipt(
            HeavensDoorBookScreen screen) {
        Iterator<StoredScreenReceipt> receipts =
                SCREEN_RECEIPTS.descendingIterator();
        while (receipts.hasNext()) {
            StoredScreenReceipt stored = receipts.next();
            if (stored.screen().get() == screen) {
                return stored.receipt();
            }
        }
        return null;
    }

    @Nullable
    public static synchronized OverlayDrawReceipt latestOverlayReceipt(
            UUID targetUuid) {
        Iterator<OverlayDrawReceipt> receipts =
                OVERLAY_RECEIPTS.descendingIterator();
        while (receipts.hasNext()) {
            OverlayDrawReceipt receipt = receipts.next();
            if (receipt.targetUuid().equals(targetUuid)) {
                return receipt;
            }
        }
        return null;
    }

    @Nullable
    public static synchronized MatchedDrawReceipt latestMatchedReceipt(
            HeavensDoorBookScreen screen) {
        ScreenDrawReceipt screenReceipt = latestScreenReceipt(screen);
        if (screenReceipt == null) {
            return null;
        }
        OverlayDrawReceipt overlayReceipt = latestOverlayReceipt(
                screenReceipt.targetUuid());
        if (overlayReceipt == null
                || overlayReceipt.frameId() != screenReceipt.frameId()
                || overlayReceipt.sequence() >= screenReceipt.sequence()) {
            return null;
        }
        return new MatchedDrawReceipt(
                screenReceipt, overlayReceipt);
    }

    private static long nextSequence() {
        sequence++;
        return sequence;
    }

    private static void trim(Deque<?> receipts) {
        while (receipts.size() > MAX_RECEIPTS) {
            receipts.removeFirst();
        }
    }

    public record TextFieldReceipt(
            String key,
            HeavensDoorBookLayout.Area area,
            List<String> renderedLines,
            long sourceTextFingerprint) {
        public TextFieldReceipt {
            key = Objects.requireNonNull(key);
            area = Objects.requireNonNull(area);
            renderedLines = List.copyOf(renderedLines);
        }
    }

    public record ScreenDrawReceipt(
            long sequence,
            long frameId,
            int screenIdentity,
            UUID targetUuid,
            int targetEntityId,
            UUID sessionNonce,
            long sessionGeneration,
            long sessionExpiresAt,
            String textureRegistryId,
            int bookLeft,
            int bookTop,
            int imageWidth,
            int imageHeight,
            int guiWidth,
            int guiHeight,
            int pageColor,
            int inkColor,
            int separatorColor,
            HeavensDoorBookLayout.Area leftPage,
            HeavensDoorBookLayout.Area rightPage,
            List<TextFieldReceipt> textFields,
            int renderableCount,
            int availableCommandMask) {
        public ScreenDrawReceipt {
            targetUuid = Objects.requireNonNull(targetUuid);
            sessionNonce = Objects.requireNonNull(sessionNonce);
            textureRegistryId = Objects.requireNonNull(textureRegistryId);
            leftPage = Objects.requireNonNull(leftPage);
            rightPage = Objects.requireNonNull(rightPage);
            textFields = List.copyOf(textFields);
        }
    }

    public record OverlayDrawReceipt(
            long sequence,
            long frameId,
            UUID targetUuid,
            int targetEntityId,
            UUID renderedEntityUuid,
            int renderedEntityId,
            UUID ownerUuid,
            String dimensionRegistryId,
            long bookGeneration,
            int bookAmplifier,
            int stage,
            String baseTextureRegistryId,
            String overlayTextureRegistryId,
            int textureWidth,
            int textureHeight,
            String rendererClass,
            String modelClass,
            int packedLight,
            int packedOverlay,
            float poseScale,
            int color,
            float effectiveAlpha) {
        public OverlayDrawReceipt {
            targetUuid = Objects.requireNonNull(targetUuid);
            renderedEntityUuid = Objects.requireNonNull(renderedEntityUuid);
            ownerUuid = Objects.requireNonNull(ownerUuid);
            dimensionRegistryId = Objects.requireNonNull(
                    dimensionRegistryId);
            baseTextureRegistryId = Objects.requireNonNull(
                    baseTextureRegistryId);
            overlayTextureRegistryId = Objects.requireNonNull(
                    overlayTextureRegistryId);
            rendererClass = Objects.requireNonNull(rendererClass);
            modelClass = Objects.requireNonNull(modelClass);
        }
    }

    public record MatchedDrawReceipt(
            ScreenDrawReceipt screen,
            OverlayDrawReceipt overlay) {
        public MatchedDrawReceipt {
            screen = Objects.requireNonNull(screen);
            overlay = Objects.requireNonNull(overlay);
        }
    }

    private record StoredScreenReceipt(
            WeakReference<HeavensDoorBookScreen> screen,
            ScreenDrawReceipt receipt) {}
}

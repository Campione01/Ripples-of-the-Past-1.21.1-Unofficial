package rotp.addons.softandwet.client;

import java.util.UUID;

import rotp.core.client.entityrender.stand.StandEntityRenderer;

import rotp.addons.softandwet.SoftAndWetAddon;
import rotp.addons.softandwet.entity.SoftAndWetBubbleEntity.Kind;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = SoftAndWetAddon.MOD_ID, value = Dist.CLIENT)
public final class SoftAndWetClientEvents {
    private static boolean diagnosticsArmed;
    private static long frustumAcceptCount;
    private static long lastFrustumFrame = Long.MIN_VALUE;
    private static long drawCount;
    private static long lastDrawFrame = Long.MIN_VALUE;
    private static UUID lastFrustumBubbleUuid;
    private static UUID lastDrawBubbleUuid;
    private static UUID lastOwnerUuid;
    private static Kind lastKind;
    private static ResourceLocation lastTexture;
    private static float lastEffectiveAlpha;

    private SoftAndWetClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                SoftAndWetAddon.SOFT_AND_WET_ENTITY.get(),
                StandEntityRenderer::new);
        event.registerEntityRenderer(
                SoftAndWetAddon.BUBBLE_ENTITY.get(),
                SoftAndWetBubbleRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                SoftAndWetBubbleModel.LAYER_LOCATION,
                SoftAndWetBubbleModel::createBodyLayer);
    }

    static synchronized void recordBubbleFrustum(
            UUID bubbleUuid, long frameId) {
        if (!diagnosticsArmed) {
            return;
        }
        frustumAcceptCount++;
        lastFrustumFrame = frameId;
        lastFrustumBubbleUuid = bubbleUuid;
    }

    static synchronized void recordBubbleDraw(
            UUID bubbleUuid,
            UUID ownerUuid,
            Kind kind,
            ResourceLocation texture,
            float effectiveAlpha,
            long frameId) {
        if (!diagnosticsArmed) {
            return;
        }
        drawCount++;
        lastDrawFrame = frameId;
        lastDrawBubbleUuid = bubbleUuid;
        lastOwnerUuid = ownerUuid;
        lastKind = kind;
        lastTexture = texture;
        lastEffectiveAlpha = effectiveAlpha;
    }

    public static synchronized void resetDiagnostics() {
        diagnosticsArmed = true;
        frustumAcceptCount = 0L;
        lastFrustumFrame = Long.MIN_VALUE;
        drawCount = 0L;
        lastDrawFrame = Long.MIN_VALUE;
        lastFrustumBubbleUuid = null;
        lastDrawBubbleUuid = null;
        lastOwnerUuid = null;
        lastKind = null;
        lastTexture = null;
        lastEffectiveAlpha = 0.0F;
    }

    public static synchronized DiagnosticSnapshot diagnosticSnapshot() {
        return new DiagnosticSnapshot(
                frustumAcceptCount,
                lastFrustumFrame,
                drawCount,
                lastDrawFrame,
                lastFrustumBubbleUuid,
                lastDrawBubbleUuid,
                lastOwnerUuid,
                lastKind,
                lastTexture,
                lastEffectiveAlpha);
    }

    public record DiagnosticSnapshot(
            long frustumAcceptCount,
            long lastFrustumFrame,
            long drawCount,
            long lastDrawFrame,
            UUID lastFrustumBubbleUuid,
            UUID lastDrawBubbleUuid,
            UUID lastOwnerUuid,
            Kind lastKind,
            ResourceLocation lastTexture,
            float lastEffectiveAlpha) {}
}

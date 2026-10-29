package rotp.addons.ger.client;

import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.network.GerRtzPayloads.StartPayload;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ViewportEvent;

@OnlyIn(Dist.CLIENT)
public final class GerRtzGoldenFog {
    public static final float TARGET_RED = 0.92F;
    public static final float TARGET_GREEN = 0.76F;
    public static final float TARGET_BLUE = 0.24F;
    public static final float TARGET_DENSITY = 0.005F;
    public static final float FADE_IN_SPEED = 0.18F;
    public static final float FADE_OUT_SPEED = 0.16F;

    private GerRtzGoldenFog() {}

    public static void applyColor(
            ViewportEvent.ComputeFogColor event) {
        float intensity =
                intensity((float) event.getPartialTick());
        if (intensity <= 0.0F) {
            return;
        }
        event.setRed(Mth.lerp(
                intensity, event.getRed(), TARGET_RED));
        event.setGreen(Mth.lerp(
                intensity, event.getGreen(), TARGET_GREEN));
        event.setBlue(Mth.lerp(
                intensity, event.getBlue(), TARGET_BLUE));
        GerRtzDiagnostics.recordFogApply();
    }

    public static void applyDensity(
            ViewportEvent.RenderFog event) {
        float intensity =
                intensity((float) event.getPartialTick());
        if (intensity <= 0.0F) {
            return;
        }
        float originalNear = event.getNearPlaneDistance();
        float originalFar = event.getFarPlaneDistance();
        float densityFar = 1.0F / TARGET_DENSITY;
        float targetFar = Math.min(originalFar, densityFar);
        event.setNearPlaneDistance(Mth.lerp(
                intensity, originalNear, 0.0F));
        event.setFarPlaneDistance(Mth.lerp(
                intensity, originalFar, targetFar));
        event.setCanceled(true);
        GerRtzDiagnostics.recordFogApply();
    }

    static float intensity(float partialTick) {
        StartPayload active = GerRtzClientState.active();
        if (active == null
                || GerRtzClientState.activeMode()
                        != Mode.NORMAL) {
            return 0.0F;
        }
        float elapsed =
                GerRtzClientState.elapsedTicks() + partialTick;
        float remaining =
                GerRtzClientState.presentationTotalTicks(active)
                        - elapsed;
        float fadeIn = Mth.clamp(
                elapsed * FADE_IN_SPEED, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(
                remaining * FADE_OUT_SPEED, 0.0F, 1.0F);
        return Math.min(fadeIn, fadeOut);
    }
}

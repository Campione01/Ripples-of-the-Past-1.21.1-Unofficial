package rotp.addons.ger.client;

import rotp.core.api.client.render.ClientRenderCompatibility;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

public final class GerRtzGoldenFogShaderPackLayer
        implements LayeredDraw.Layer {
    private static final float REFERENCE_DISTANCE = 64.0F;
    private static final float MAX_ALPHA =
            1.0F - (float) Math.exp(
                    -GerRtzGoldenFog.TARGET_DENSITY
                            * REFERENCE_DISTANCE);

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!ClientRenderCompatibility.snapshot()
                .irisShaderPackInUse()) {
            return;
        }
        float intensity = GerRtzGoldenFog.intensity(
                deltaTracker.getGameTimeDeltaPartialTick(false));
        if (intensity <= 0.0F) {
            return;
        }

        int alpha = Mth.clamp(
                Math.round(intensity * MAX_ALPHA * 255.0F),
                0,
                255);
        int red = Math.round(
                GerRtzGoldenFog.TARGET_RED * 255.0F);
        int green = Math.round(
                GerRtzGoldenFog.TARGET_GREEN * 255.0F);
        int blue = Math.round(
                GerRtzGoldenFog.TARGET_BLUE * 255.0F);
        int color = alpha << 24
                | red << 16
                | green << 8
                | blue;
        graphics.fill(
                0,
                0,
                graphics.guiWidth(),
                graphics.guiHeight(),
                color);
        graphics.flush();
        GerRtzDiagnostics.recordFogApply();
    }
}

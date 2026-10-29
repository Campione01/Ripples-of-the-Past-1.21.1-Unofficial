package rotp.addons.kingcrimson.client;

import rotp.core.api.client.render.ClientRenderCompatibility;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/** Final-main-target color grade for Time Erase under Iris shaders. */
public final class KingCrimsonTimeEraseIrisOverlay
        implements LayeredDraw.Layer {
    private static final ResourceLocation OVERLAY =
            KingCrimsonAddon.id(
                    "textures/environment/time_erase_iris_overlay.png");
    private static final int BACKGROUND_TINT = 0xE00C0000;
    private static final float OVERLAY_ALPHA = 0.60F;
    private static final float UV_SPAN = 0.78F;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (!KingCrimsonTemporalClientState.localTimeErase()
                || !ClientRenderCompatibility.snapshot()
                        .irisShaderPackInUse()) {
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        try {
            RenderSystem.defaultBlendFunc();
            graphics.fill(0, 0, width, height, BACKGROUND_TINT);
            graphics.flush();
            // flushing the fill ends the gui render type, which turns blending off and depth testing back on, so the
            // semi-transparent overlay below would otherwise draw as opaque blotches
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            Minecraft.getInstance().getTextureManager()
                    .getTexture(OVERLAY)
                    .setFilter(true, false);
            float animation = (Minecraft.getInstance().level.getGameTime()
                    + deltaTracker.getGameTimeDeltaPartialTick(false))
                    * 0.015F;
            float u0 = 0.11F
                    + (float) Math.sin(animation) * 0.09F;
            float v0 = 0.11F
                    + (float) Math.cos(animation * 0.83F) * 0.09F;
            drawOverlay(
                    graphics,
                    width,
                    height,
                    u0,
                    v0,
                    u0 + UV_SPAN,
                    v0 + UV_SPAN);
        }
        finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawOverlay(
            GuiGraphics graphics,
            int width,
            int height,
            float minU,
            float minV,
            float maxU,
            float maxV) {
        RenderSystem.setShaderTexture(0, OVERLAY);
        RenderSystem.setShader(
                GameRenderer::getPositionTexColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR);
        var matrix = graphics.pose().last().pose();
        builder.addVertex(matrix, 0.0F, 0.0F, -90.0F)
                .setUv(minU, minV)
                .setColor(1.0F, 1.0F, 1.0F, OVERLAY_ALPHA);
        builder.addVertex(matrix, 0.0F, height, -90.0F)
                .setUv(minU, maxV)
                .setColor(1.0F, 1.0F, 1.0F, OVERLAY_ALPHA);
        builder.addVertex(matrix, width, height, -90.0F)
                .setUv(maxU, maxV)
                .setColor(1.0F, 1.0F, 1.0F, OVERLAY_ALPHA);
        builder.addVertex(matrix, width, 0.0F, -90.0F)
                .setUv(maxU, minV)
                .setColor(1.0F, 1.0F, 1.0F, OVERLAY_ALPHA);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }
}

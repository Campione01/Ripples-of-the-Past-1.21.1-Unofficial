package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class KingCrimsonTemporalOverlay
        implements LayeredDraw.Layer {
    private static final int TIME_SKIP_WIDTH = 256;
    private static final int TIME_SKIP_HEIGHT = 144;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // 1.16 sent this flash only to the user and to nearby players whose own Stand is King Crimson
        // (VFXServerHelper), and like the rest of its in-game GUI it was not drawn with F1
        if (minecraft.options.hideGui
                || minecraft.player == null
                || !KingCrimsonAddon.isKingCrimsonUser(minecraft.player)
                || !KingCrimsonTemporalClientState
                        .timeEraseTransitionActive()) {
            return;
        }
        drawTimeSkip(
                graphics,
                KingCrimsonTemporalClientState
                        .timeEraseTransitionFrame());
    }

    private static void drawTimeSkip(
            GuiGraphics graphics, int frame) {
        ResourceLocation texture = KingCrimsonAddon.id(
                "textures/timeskip/time_skip"
                        + frame
                        + ".png");
        drawScaled(
                graphics,
                texture,
                TIME_SKIP_WIDTH,
                TIME_SKIP_HEIGHT);
    }

    private static void drawScaled(
            GuiGraphics graphics,
            ResourceLocation texture,
            int width,
            int height) {
        float scaleX = (float) graphics.guiWidth() / width;
        float scaleY = (float) graphics.guiHeight() / height;
        // the frames are mostly semi-transparent; 1.16 drew them with blending on
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.blit(
                texture,
                0,
                0,
                0,
                0,
                width,
                height,
                width,
                height);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }
}

package rotp.addons.cream.client;

import rotp.addons.cream.CreamAddon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class CreamVoidOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation INSIDE_CREAM =
            CreamAddon.id("textures/gui/inside_cream.png");
    private static final int TEXTURE_WIDTH = 854;
    private static final int TEXTURE_HEIGHT = 480;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.hasEffect(
                CreamAddon.TRUE_BLINDNESS)) {
            graphics.fill(
                    0,
                    0,
                    graphics.guiWidth(),
                    graphics.guiHeight(),
                    0xFF000000);
            return;
        }
        if (!minecraft.player.hasEffect(
                CreamAddon.INSIDE_CREAM)
                || !minecraft.options.getCameraType()
                        .isFirstPerson()) {
            return;
        }

        float scaleX =
                (float) graphics.guiWidth() / TEXTURE_WIDTH;
        float scaleY =
                (float) graphics.guiHeight() / TEXTURE_HEIGHT;
        // the void texture's edges are semi-transparent; 1.16 drew it with blending on
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.pose().pushPose();
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.blit(
                INSIDE_CREAM,
                0,
                0,
                0,
                0,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        graphics.pose().popPose();
        RenderSystem.disableBlend();
    }
}

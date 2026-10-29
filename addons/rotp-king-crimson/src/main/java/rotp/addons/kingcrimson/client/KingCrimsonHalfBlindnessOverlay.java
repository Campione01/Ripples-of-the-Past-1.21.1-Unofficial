package rotp.addons.kingcrimson.client;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class KingCrimsonHalfBlindnessOverlay
        implements LayeredDraw.Layer {
    private static final ResourceLocation LEFT =
            KingCrimsonAddon.id("textures/gui/leftb.png");
    private static final ResourceLocation RIGHT =
            KingCrimsonAddon.id("textures/gui/rightb.png");
    private static final int TEXTURE_WIDTH = 854;
    private static final int TEXTURE_HEIGHT = 480;

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // 1.16 drew this in the in-game GUI pass, which F1 skips; unlike Cream, King Crimson never forced the HUD on
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        // 1.16 had one renderer per eye, so with both effects (two eye jabs can pick different sides) both halves
        // were covered; the port's else-if drew only the left one
        if (minecraft.player.hasEffect(
                KingCrimsonAddon.HALF_BLINDNESS_LEFT)) {
            draw(graphics, LEFT);
        }
        if (minecraft.player.hasEffect(
                KingCrimsonAddon.HALF_BLINDNESS_RIGHT)) {
            draw(graphics, RIGHT);
        }
    }

    private static void draw(
            GuiGraphics graphics, ResourceLocation texture) {
        float scaleX =
                (float) graphics.guiWidth() / TEXTURE_WIDTH;
        float scaleY =
                (float) graphics.guiHeight() / TEXTURE_HEIGHT;
        graphics.pose().pushPose();
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.blit(
                texture,
                0,
                0,
                0,
                0,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT);
        graphics.pose().popPose();
    }
}

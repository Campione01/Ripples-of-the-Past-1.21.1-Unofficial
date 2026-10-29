package rotp.addons.scarymonsters.client;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class ScaryMonstersOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation DRAGON = ScaryMonstersAddon.id(
            "textures/overlay/dragon_vignette.png");
    // 1.16 DragonFormOverlay: the vignette fades in over 160 rendered frames from the moment Dragon Form starts
    private static final int FADE_IN_FRAMES = 160;
    private static int dragonFrames;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        if (minecraft.player.hasEffect(ScaryMonstersAddon.DRAGON_FORM)) {
            if (dragonFrames < FADE_IN_FRAMES) dragonFrames++;
            // the vignette has no opaque pixel at all; without blending every tinted pixel drew as solid cyan
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor(1.0F, 1.0F, 1.0F, (float) dragonFrames / FADE_IN_FRAMES);
            graphics.blit(
                    DRAGON,
                    0, 0, 0, 0,
                    graphics.guiWidth(), graphics.guiHeight(),
                    graphics.guiWidth(), graphics.guiHeight());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
            return;
        }
        dragonFrames = 0;
    }
}

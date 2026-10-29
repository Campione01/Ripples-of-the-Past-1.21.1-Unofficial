package rotp.addons.badcompany.client;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyRemoteHud
        implements LayeredDraw.Layer {
    private static final ResourceLocation REMOTE =
            BadCompanyAddon.id("textures/gui/remote.png");

    @Override
    public void render(
            GuiGraphics graphics,
            DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || minecraft.player == null
                || !minecraft.player.isAlive()) {
            return;
        }
        StandPower power = StandPower.get(minecraft.player);
        if (power == null
                || power.getPowerType()
                        != BadCompanyAddon.BAD_COMPANY.get()
                || !(power.getSummonedStandEntity()
                        instanceof BadCompanyStandEntity
                                coordinator)
                || !coordinator.isManuallyControlled()) {
            return;
        }
        ResourceLocation texture =
                BadCompanyVisuals.texture(
                        coordinator, REMOTE);
        // 1.16 (AerialScreen) stretched the whole 256x144 frame over the screen; declaring 256x144 here tiled it
        // at 1:1 from the top-left corner instead. The frame is semi-transparent, so blending must be on.
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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
        RenderSystem.disableBlend();
    }
}

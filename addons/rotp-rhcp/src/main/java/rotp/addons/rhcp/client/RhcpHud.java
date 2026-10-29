package rotp.addons.rhcp.client;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.state.RhcpState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;

public final class RhcpHud implements LayeredDraw.Layer {
    private static final ResourceLocation BATTERY =
            RhcpAddon.id("textures/gui/battery.png");

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || !minecraft.player.isAlive()) {
            return;
        }
        StandPower power = StandPower.get(minecraft.player);
        if (power == null
                || power.getPowerType() != RhcpAddon.RHCP.get()
                || !power.isSummoned()) {
            return;
        }
        RhcpState state = RhcpAddon.state(minecraft.player);
        if (state.energy() <= 0.0F) {
            return;
        }
        // 1.16 put the battery right of the crosshair, with the sprite 4 px in, while the ability hotbars sit in the
        // top left (its default, and where the port's HUD always is); left of the crosshair only for other layouts.
        int x = graphics.guiWidth() / 2 + 9;
        int y = graphics.guiHeight() / 2 - 9;
        int spriteU = 4;
        // semi-transparent edge pixels: 1.16 drew with blending on
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(
                BATTERY,
                x,
                y,
                spriteU,
                0,
                18,
                18,
                256,
                256);
        float ratio = Math.max(
                0.0F,
                Math.min(1.0F, state.energy() / RhcpState.MAX_ENERGY));
        int fill = Math.max(1, Math.min(18, (int) Math.ceil(ratio * 18.0F)));
        if (fill > 0) {
            int fillU = spriteU + (ratio > 0.75F ? 90
                    : ratio > 0.5F ? 72
                            : ratio > 0.25F ? 54
                                    : ratio > 0.125F ? 36 : 18);
            int potencyV = switch (state.potency()) {
                case 2 -> 20;
                case 3 -> 40;
                case 4 -> 60;
                case 5 -> 80;
                default -> 0;
            };
            graphics.blit(
                    BATTERY,
                    x,
                    y + 18 - fill,
                    fillU,
                    potencyV + 18 - fill,
                    18,
                    fill,
                    256,
                    256);
        }
        RenderSystem.disableBlend();
    }
}

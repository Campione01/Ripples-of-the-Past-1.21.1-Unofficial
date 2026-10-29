package rotp.addons.speedwagon.client;

import rotp.core.powersystem.playerpower.PlayerPower;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.power.SpeedwagonData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class SpeedwagonEnergyHud
        implements LayeredDraw.Layer {
    private static final int BAR_WIDTH = 92;
    private static final int BAR_HEIGHT = 7;

    @Override
    public void render(
            GuiGraphics graphics,
            DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || minecraft.player == null) {
            return;
        }
        SpeedwagonData data = PlayerPower.getPowerData(
                minecraft.player, SpeedwagonAddon.SPEEDWAGON)
                .orElse(null);
        if (data == null) {
            return;
        }

        int x = 8;
        int y = 8;
        Component label = Component.translatable(
                "rotp_sp.hud.energy",
                Math.round(data.getEnergy()),
                Math.round(SpeedwagonData.MAX_ENERGY));
        graphics.drawString(
                minecraft.font,
                label,
                x,
                y,
                0xFFF0E6D2,
                true);

        int barY = y + minecraft.font.lineHeight + 2;
        graphics.fill(
                x,
                barY,
                x + BAR_WIDTH,
                barY + BAR_HEIGHT,
                0xCC17181B);
        graphics.fill(
                x + 1,
                barY + 1,
                x + BAR_WIDTH - 1,
                barY + BAR_HEIGHT - 1,
                0xFF4A3E43);
        int fill = Mth.floor(
                (BAR_WIDTH - 2)
                        * Mth.clamp(
                                data.getEnergyRatio(),
                                0.0F,
                                1.0F));
        if (fill > 0) {
            graphics.fill(
                    x + 1,
                    barY + 1,
                    x + 1 + fill,
                    barY + BAR_HEIGHT - 1,
                    0xFFD9B44A);
        }
    }
}

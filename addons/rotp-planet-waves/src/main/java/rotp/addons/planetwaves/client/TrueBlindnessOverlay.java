package rotp.addons.planetwaves.client;

import rotp.addons.planetwaves.PlanetWavesAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;

public final class TrueBlindnessOverlay
        implements LayeredDraw.Layer {
    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !minecraft.player.hasEffect(
                        PlanetWavesAddon.TRUE_BLINDNESS)) {
            return;
        }
        graphics.fill(
                0,
                0,
                graphics.guiWidth(),
                graphics.guiHeight(),
                0xFF000000);
    }
}

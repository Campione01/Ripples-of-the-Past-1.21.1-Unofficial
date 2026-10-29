package rotp.addons.d4c.client;

import rotp.addons.d4c.network.OpenDimensionSelectorPayload;
import rotp.addons.d4c.network.OpenMinionAiSelectorPayload;
import net.minecraft.client.Minecraft;

public final class D4CClientPayloads {
    private D4CClientPayloads() {}

    public static void openDimensionSelector(
            OpenDimensionSelectorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen == null
                || minecraft.screen instanceof DimensionSelectorScreen) {
            minecraft.setScreen(new DimensionSelectorScreen(payload));
        }
    }

    // 1.16 ak/aa/aa/ai.java set the selector whatever screen was open
    public static void openMinionAiSelector(
            OpenMinionAiSelectorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        minecraft.setScreen(new MinionAiSelectorScreen());
    }
}

package rotp.addons.kraftwork.client;

import rotp.addons.kraftwork.network.OpenAdvancedReleasePayload;
import net.minecraft.client.Minecraft;

public final class KraftWorkClientPayloads {
    private KraftWorkClientPayloads() {}

    public static void openAdvancedRelease(
            OpenAdvancedReleasePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen == null
                || minecraft.screen
                        instanceof AdvancedReleaseScreen) {
            minecraft.setScreen(new AdvancedReleaseScreen(
                    payload.sessionId()));
        }
    }
}

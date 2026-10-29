package rotp.addons.badcompany.client;

import rotp.addons.badcompany.network.OpenBadCompanySelectionPayload;
import net.minecraft.client.Minecraft;

final class BadCompanyClientPayloads {
    private BadCompanyClientPayloads() {}

    static void openSelection(
            OpenBadCompanySelectionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && minecraft.screen == null) {
            minecraft.setScreen(
                    new BadCompanySelectionScreen(payload));
        }
    }
}

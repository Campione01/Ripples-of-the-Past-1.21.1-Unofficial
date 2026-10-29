package rotp.addons.weatherreport.client;

import rotp.addons.weatherreport.network.OpenWeatherSelectionPayload;
import net.minecraft.client.Minecraft;

public final class WeatherReportClientPayloads {
    private WeatherReportClientPayloads() {}

    public static void openSelection(
            OpenWeatherSelectionPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!(minecraft.screen instanceof WeatherSelectionScreen screen)
                || !screen.hasSession(payload.sessionId())) {
            minecraft.setScreen(new WeatherSelectionScreen(payload));
        }
    }
}

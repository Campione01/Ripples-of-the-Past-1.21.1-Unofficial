package rotp.addons.weatherreport.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class WeatherReportPayloads {
    private static Consumer<OpenWeatherSelectionPayload> clientOpenHandler =
            payload -> {};

    private WeatherReportPayloads() {}

    public static ResourceLocation id(String path) {
        return WeatherReportAddon.id(path);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1")
                .executesOn(HandlerThread.MAIN);
        registrar.playToClient(
                OpenWeatherSelectionPayload.TYPE,
                OpenWeatherSelectionPayload.STREAM_CODEC,
                WeatherReportPayloads::handleOpenSelection);
        registrar.playToServer(
                SelectWeatherPayload.TYPE,
                SelectWeatherPayload.STREAM_CODEC,
                WeatherReportPayloads::handleSelection);
    }

    public static void installClientOpenHandler(
            Consumer<OpenWeatherSelectionPayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    private static void handleOpenSelection(
            OpenWeatherSelectionPayload payload,
            IPayloadContext context) {
        clientOpenHandler.accept(payload);
    }

    private static void handleSelection(
            SelectWeatherPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            WeatherSelectionSessions.consumeAndApply(
                    player,
                    payload.sessionId(),
                    payload.choice());
        }
    }
}

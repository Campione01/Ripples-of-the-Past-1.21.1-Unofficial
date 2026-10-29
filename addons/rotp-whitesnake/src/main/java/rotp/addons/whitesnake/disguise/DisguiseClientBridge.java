package rotp.addons.whitesnake.disguise;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.addons.whitesnake.network.OpenDisguiseSelectionPayload;

public final class DisguiseClientBridge {
    private static Consumer<OpenDisguiseSelectionPayload> openHandler =
            payload -> {};

    private DisguiseClientBridge() {}

    public static void install(
            Consumer<OpenDisguiseSelectionPayload> handler) {
        openHandler = Objects.requireNonNull(handler);
    }

    public static void openSelection(
            OpenDisguiseSelectionPayload payload) {
        openHandler.accept(Objects.requireNonNull(payload));
    }
}

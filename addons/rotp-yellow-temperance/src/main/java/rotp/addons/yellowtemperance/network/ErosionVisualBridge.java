package rotp.addons.yellowtemperance.network;

import java.util.Objects;

public final class ErosionVisualBridge {
    @FunctionalInterface
    public interface Handler {
        void apply(int targetId, int controllerId, boolean active);
    }

    private static Handler handler = (target, controller, active) -> {};

    private ErosionVisualBridge() {}

    public static void install(Handler clientHandler) {
        handler = Objects.requireNonNull(clientHandler);
    }

    public static void apply(
            int targetId, int controllerId, boolean active) {
        handler.apply(targetId, controllerId, active);
    }
}

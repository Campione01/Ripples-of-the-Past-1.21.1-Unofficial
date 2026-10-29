package rotp.addons.khnum.disguise;

import java.util.Objects;
import java.util.function.Consumer;

public final class DisguiseClientBridge {
    private static Consumer<DisguiseMode> openHandler =
            ignored -> {};

    private DisguiseClientBridge() {}

    public static void install(Consumer<DisguiseMode> handler) {
        openHandler = Objects.requireNonNull(handler);
    }

    public static void open(DisguiseMode mode) {
        openHandler.accept(mode);
    }
}

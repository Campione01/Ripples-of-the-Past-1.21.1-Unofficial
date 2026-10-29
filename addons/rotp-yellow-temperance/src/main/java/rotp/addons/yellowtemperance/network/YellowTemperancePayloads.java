package rotp.addons.yellowtemperance.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class YellowTemperancePayloads {
    private YellowTemperancePayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(
                DisguiseSelectionPayload.TYPE,
                DisguiseSelectionPayload.STREAM_CODEC,
                DisguiseSelectionPayload::handle);
        registrar.playToClient(
                ErosionVisualPayload.TYPE,
                ErosionVisualPayload.STREAM_CODEC,
                ErosionVisualPayload::handle);
    }
}

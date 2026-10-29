package rotp.addons.shadowtheworld.network;

import java.util.Objects;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 OpenStandPanelPacket: after an evolution to The World the server
 * tells the user's client to switch its HUD to the Stand controls
 * (ActionsOverlayGui.onStandSummon).
 */
@EventBusSubscriber(
        modid = ShadowTheWorldAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public record ShadowStandPanelPayload() implements CustomPacketPayload {
    public static final String NETWORK_PROTOCOL_VERSION = "1";
    public static final ShadowStandPanelPayload INSTANCE =
            new ShadowStandPanelPayload();
    public static final Type<ShadowStandPanelPayload> TYPE =
            new Type<>(ShadowTheWorldAddon.id("open_stand_panel"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, ShadowStandPanelPayload>
            STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private static Runnable clientHandler = () -> {};

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION)
                .optional()
                .playToClient(
                        TYPE,
                        STREAM_CODEC,
                        ShadowStandPanelPayload::handle);
    }

    public static void installClientHandler(Runnable handler) {
        clientHandler = Objects.requireNonNull(handler);
    }

    private static void handle(
            ShadowStandPanelPayload payload,
            IPayloadContext context) {
        clientHandler.run();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package rotp.addons.d4c.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BService;
import rotp.addons.d4c.travel.D4CDimensionTravelService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class D4CPayloads {
    private static Consumer<OpenDimensionSelectorPayload> clientOpenHandler =
            payload -> {};
    private static Consumer<LoveTrainPresentationPayload>
            clientLoveTrainHandler = payload -> {};
    private static Consumer<SaintCorpseXrayGlowPayload>
            clientXrayGlowHandler = payload -> {};
    private static Consumer<DevilPalmMarkerPayload>
            clientDevilPalmMarkerHandler = payload -> {};
    private static Consumer<OpenMinionAiSelectorPayload>
            clientMinionAiSelectorHandler = payload -> {};

    private D4CPayloads() {}

    public static ResourceLocation id(String path) {
        return D4CAddon.id(path);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2a")
                .optional()
                .executesOn(
                        net.neoforged.neoforge.network.registration
                                .HandlerThread.MAIN);
        registrar.playToClient(
                OpenDimensionSelectorPayload.TYPE,
                OpenDimensionSelectorPayload.STREAM_CODEC,
                D4CPayloads::handleOpenSelector);
        registrar.playToClient(
                LoveTrainPresentationPayload.TYPE,
                LoveTrainPresentationPayload.STREAM_CODEC,
                D4CPayloads::handleLoveTrainPresentation);
        registrar.playToServer(
                SelectDimensionPayload.TYPE,
                SelectDimensionPayload.STREAM_CODEC,
                D4CPayloads::handleSelection);
        registrar.playToClient(
                SaintCorpseXrayGlowPayload.TYPE,
                SaintCorpseXrayGlowPayload.STREAM_CODEC,
                (payload, context) -> clientXrayGlowHandler.accept(payload));
        registrar.playToClient(
                DevilPalmMarkerPayload.TYPE,
                DevilPalmMarkerPayload.STREAM_CODEC,
                (payload, context) ->
                        clientDevilPalmMarkerHandler.accept(payload));
        registrar.playToClient(
                AnnihilationMarkPayload.TYPE,
                AnnihilationMarkPayload.STREAM_CODEC,
                (payload, context) -> D4CPhase2BService.acceptClientMark(
                        payload.entityId()));
        registrar.playToClient(
                OpenMinionAiSelectorPayload.TYPE,
                OpenMinionAiSelectorPayload.STREAM_CODEC,
                (payload, context) ->
                        clientMinionAiSelectorHandler.accept(payload));
        registrar.playToServer(
                SelectMinionAiModePayload.TYPE,
                SelectMinionAiModePayload.STREAM_CODEC,
                D4CPayloads::handleMinionAiSelection);
    }

    public static void installClientMinionAiSelectorHandler(
            Consumer<OpenMinionAiSelectorPayload> handler) {
        clientMinionAiSelectorHandler = Objects.requireNonNull(handler);
    }

    private static void handleMinionAiSelection(
            SelectMinionAiModePayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            D4CPhase2BService.selectMinionAiMode(player, payload.mode());
        }
    }

    public static void installClientXrayGlowHandler(
            Consumer<SaintCorpseXrayGlowPayload> handler) {
        clientXrayGlowHandler = Objects.requireNonNull(handler);
    }

    public static void installClientDevilPalmMarkerHandler(
            Consumer<DevilPalmMarkerPayload> handler) {
        clientDevilPalmMarkerHandler = Objects.requireNonNull(handler);
    }

    public static void installClientOpenHandler(
            Consumer<OpenDimensionSelectorPayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static void installClientLoveTrainHandler(
            Consumer<LoveTrainPresentationPayload> handler) {
        clientLoveTrainHandler = Objects.requireNonNull(handler);
    }

    private static void handleOpenSelector(
            OpenDimensionSelectorPayload payload,
            IPayloadContext context) {
        clientOpenHandler.accept(payload);
    }

    private static void handleLoveTrainPresentation(
            LoveTrainPresentationPayload payload,
            IPayloadContext context) {
        clientLoveTrainHandler.accept(payload);
    }

    private static void handleSelection(
            SelectDimensionPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            D4CDimensionTravelService.consumeSelection(
                    player,
                    payload.sessionId(),
                    payload.target());
        }
    }
}

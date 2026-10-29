package rotp.addons.nextalbum.network;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.state.NecklaceBeamService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record NecklaceBeamUsePayload()
        implements CustomPacketPayload {
    public static final NecklaceBeamUsePayload INSTANCE =
            new NecklaceBeamUsePayload();
    public static final Type<NecklaceBeamUsePayload> TYPE =
            new Type<>(NextAlbumIds.id("necklace_beam_sync"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            NecklaceBeamUsePayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public static void register(
            RegisterPayloadHandlersEvent event) {
        // "2": adds the clientbound charge state.
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(
                TYPE,
                STREAM_CODEC,
                NecklaceBeamUsePayload::handle);
        registrar.playToClient(
                NecklaceChargeStatePayload.TYPE,
                NecklaceChargeStatePayload.STREAM_CODEC,
                NecklaceChargeStatePayload::handle);
    }

    private static void handle(
            NecklaceBeamUsePayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            NecklaceBeamService.keepAlive(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

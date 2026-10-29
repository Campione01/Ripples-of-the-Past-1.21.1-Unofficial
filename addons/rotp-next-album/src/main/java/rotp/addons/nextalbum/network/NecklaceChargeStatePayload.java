package rotp.addons.nextalbum.network;

import io.netty.buffer.ByteBuf;
import rotp.addons.nextalbum.NextAlbumIds;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to charger: a necklace charge started (true) or ended (false). */
public record NecklaceChargeStatePayload(boolean charging)
        implements CustomPacketPayload {
    public static final NecklaceChargeStatePayload STARTED =
            new NecklaceChargeStatePayload(true);
    public static final NecklaceChargeStatePayload ENDED =
            new NecklaceChargeStatePayload(false);
    public static final Type<NecklaceChargeStatePayload> TYPE =
            new Type<>(NextAlbumIds.id("necklace_charge_state"));
    public static final StreamCodec<
            ByteBuf,
            NecklaceChargeStatePayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(
                    NecklaceChargeStatePayload::new,
                    NecklaceChargeStatePayload::charging);

    // Client-side view of the server's charge; only the client
    // handler writes it, so this class stays safe on a server.
    private static volatile boolean chargeConfirmed;

    public static boolean isChargeConfirmed() {
        return chargeConfirmed;
    }

    public static void clearChargeConfirmed() {
        chargeConfirmed = false;
    }

    static void handle(
            NecklaceChargeStatePayload payload,
            IPayloadContext context) {
        chargeConfirmed = payload.charging();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

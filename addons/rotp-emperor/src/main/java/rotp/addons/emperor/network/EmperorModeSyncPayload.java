package rotp.addons.emperor.network;

import rotp.addons.emperor.EmperorAddon;
import rotp.addons.emperor.state.EmperorMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EmperorModeSyncPayload(int modeOrdinal)
        implements CustomPacketPayload {
    public static final Type<EmperorModeSyncPayload> TYPE =
            new Type<>(EmperorAddon.id("mode_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, EmperorModeSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    EmperorModeSyncPayload::modeOrdinal,
                    EmperorModeSyncPayload::new);

    public static void handle(
            EmperorModeSyncPayload payload, IPayloadContext context) {
        EmperorAddon.modeState(context.player()).setMode(
                EmperorMode.byOrdinal(payload.modeOrdinal()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

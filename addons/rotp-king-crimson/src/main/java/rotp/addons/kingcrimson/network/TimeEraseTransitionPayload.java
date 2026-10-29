package rotp.addons.kingcrimson.network;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonTemporalClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TimeEraseTransitionPayload()
        implements CustomPacketPayload {
    public static final TimeEraseTransitionPayload INSTANCE =
            new TimeEraseTransitionPayload();
    public static final Type<TimeEraseTransitionPayload> TYPE =
            new Type<>(KingCrimsonAddon.id(
                    "time_erase_transition"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TimeEraseTransitionPayload> STREAM_CODEC =
                    StreamCodec.unit(INSTANCE);

    public static void handle(
            TimeEraseTransitionPayload payload,
            IPayloadContext context) {
        context.enqueueWork(
                KingCrimsonTemporalClientState::startTimeEraseTransition);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

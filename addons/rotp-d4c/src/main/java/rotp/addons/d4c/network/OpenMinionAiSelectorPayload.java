package rotp.addons.d4c.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 1.16 ak/aa/aa/ai.java: tells the user's client to open the minion AI selector; it carries nothing. */
public record OpenMinionAiSelectorPayload() implements CustomPacketPayload {
    public static final OpenMinionAiSelectorPayload INSTANCE =
            new OpenMinionAiSelectorPayload();
    public static final Type<OpenMinionAiSelectorPayload> TYPE =
            new Type<>(D4CPayloads.id("open_minion_ai_selector"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenMinionAiSelectorPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package rotp.addons.d4c.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 1.16 ak/aa/ab/aa.java: the mode the player chose on the minion AI selector, as an int. */
public record SelectMinionAiModePayload(int mode)
        implements CustomPacketPayload {
    public static final Type<SelectMinionAiModePayload> TYPE =
            new Type<>(D4CPayloads.id("select_minion_ai_mode"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectMinionAiModePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SelectMinionAiModePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new SelectMinionAiModePayload(buffer.readInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SelectMinionAiModePayload payload) {
                    buffer.writeInt(payload.mode());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

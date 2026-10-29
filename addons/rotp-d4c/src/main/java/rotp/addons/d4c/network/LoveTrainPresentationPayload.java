package rotp.addons.d4c.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LoveTrainPresentationPayload(
        UUID owner,
        Signal signal) implements CustomPacketPayload {
    public static final Type<LoveTrainPresentationPayload> TYPE =
            new Type<>(D4CPayloads.id("love_train_presentation"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            LoveTrainPresentationPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LoveTrainPresentationPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    UUID owner = buffer.readUUID();
                    int ordinal = buffer.readUnsignedByte();
                    Signal[] signals = Signal.values();
                    if (ordinal >= signals.length) {
                        throw new IllegalArgumentException(
                                "Invalid Love Train presentation signal: "
                                        + ordinal);
                    }
                    return new LoveTrainPresentationPayload(
                            owner, signals[ordinal]);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        LoveTrainPresentationPayload payload) {
                    buffer.writeUUID(payload.owner());
                    buffer.writeByte(payload.signal().ordinal());
                }
            };

    public LoveTrainPresentationPayload {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(signal, "signal");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Signal {
        START,
        COMMIT,
        CANCEL
    }
}

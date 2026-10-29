package rotp.addons.hermitpurple.network;

import java.util.Objects;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectHermitTargetPayload(
        int mode, String target)
        implements CustomPacketPayload {
    private static final int MAX_TEXT = 256;

    public static final Type<SelectHermitTargetPayload> TYPE =
            new Type<>(HermitTargetPayloads.id("select_target"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectHermitTargetPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    SelectHermitTargetPayload::write,
                    SelectHermitTargetPayload::new);

    public SelectHermitTargetPayload {
        Objects.requireNonNull(target);
        if (target.length() > MAX_TEXT) {
            throw new IllegalArgumentException(
                    "Hermit Purple target is too long");
        }
    }

    private SelectHermitTargetPayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readUtf(MAX_TEXT));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(mode);
        buffer.writeUtf(target, MAX_TEXT);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

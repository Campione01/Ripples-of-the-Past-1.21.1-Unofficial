package rotp.addons.kraftwork.network;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenAdvancedReleasePayload(UUID sessionId)
        implements CustomPacketPayload {
    public static final Type<OpenAdvancedReleasePayload> TYPE =
            new Type<>(KraftWorkPayloads.id(
                    "open_advanced_release"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenAdvancedReleasePayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    OpenAdvancedReleasePayload::write,
                    OpenAdvancedReleasePayload::new);

    public OpenAdvancedReleasePayload {
        Objects.requireNonNull(sessionId);
    }

    private OpenAdvancedReleasePayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

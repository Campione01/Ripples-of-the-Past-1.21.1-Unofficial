package rotp.addons.kraftwork.network;

import java.util.Objects;
import java.util.UUID;

import rotp.addons.kraftwork.state.KraftWorkLockService.ReleaseFilter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectReleasePayload(
        UUID sessionId,
        int selection)
        implements CustomPacketPayload {
    public static final Type<SelectReleasePayload> TYPE =
            new Type<>(KraftWorkPayloads.id("select_release"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectReleasePayload> STREAM_CODEC =
            StreamCodec.ofMember(
                     SelectReleasePayload::write,
                     SelectReleasePayload::new);

    public SelectReleasePayload {
        Objects.requireNonNull(sessionId);
    }

    private SelectReleasePayload(
            RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(sessionId);
        buffer.writeVarInt(selection);
    }

    public ReleaseFilter filter() {
        return switch (selection) {
            case 0 -> ReleaseFilter.ALL;
            case 1 -> ReleaseFilter.READY;
            case 2 -> ReleaseFilter.NOT_READY;
            case 3 -> ReleaseFilter.ENDER_PEARL;
            case 4 -> ReleaseFilter.BENEFICIAL;
            default -> null;
        };
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

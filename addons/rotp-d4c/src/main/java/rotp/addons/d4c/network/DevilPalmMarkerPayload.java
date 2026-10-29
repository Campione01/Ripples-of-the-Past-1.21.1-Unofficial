package rotp.addons.d4c.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → nearby players: a Corpse Finder Devil's Palm appeared at {@code pos}; the client raises the
 * {@code rotp_d4c:devil_sky} marker 35 blocks above it (donor {@code com.mrlang.rotp_d4c.ak.aa.aa.ak}).
 */
public record DevilPalmMarkerPayload(BlockPos pos)
        implements CustomPacketPayload {
    public static final Type<DevilPalmMarkerPayload> TYPE =
            new Type<>(D4CPayloads.id("devil_palm_marker"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DevilPalmMarkerPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public DevilPalmMarkerPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new DevilPalmMarkerPayload(buffer.readBlockPos());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        DevilPalmMarkerPayload payload) {
                    buffer.writeBlockPos(payload.pos());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

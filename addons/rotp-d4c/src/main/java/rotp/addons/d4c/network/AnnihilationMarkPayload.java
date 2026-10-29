package rotp.addons.d4c.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → the marking player: the entity its Annihilation Mark is on, or -1 for none (donor
 * {@code com.mrlang.rotp_d4c.ak.aa.aa.aa}), so that the user's own client checks annihilation_mark and annihilation
 * as the server does.
 */
public record AnnihilationMarkPayload(int entityId)
        implements CustomPacketPayload {
    public static final int NONE = -1;
    public static final Type<AnnihilationMarkPayload> TYPE =
            new Type<>(D4CPayloads.id("annihilation_mark"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            AnnihilationMarkPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AnnihilationMarkPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new AnnihilationMarkPayload(buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        AnnihilationMarkPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

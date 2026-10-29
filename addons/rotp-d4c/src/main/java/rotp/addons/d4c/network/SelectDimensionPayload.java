package rotp.addons.d4c.network;

import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectDimensionPayload(
        UUID sessionId,
        ResourceLocation target) implements CustomPacketPayload {
    public static final Type<SelectDimensionPayload> TYPE =
            new Type<>(D4CPayloads.id("select_dimension"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SelectDimensionPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SelectDimensionPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new SelectDimensionPayload(
                            buffer.readUUID(),
                            buffer.readResourceLocation());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SelectDimensionPayload payload) {
                    buffer.writeUUID(payload.sessionId());
                    buffer.writeResourceLocation(payload.target());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

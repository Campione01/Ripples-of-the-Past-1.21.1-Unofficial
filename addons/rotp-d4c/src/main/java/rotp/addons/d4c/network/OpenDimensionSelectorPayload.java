package rotp.addons.d4c.network;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenDimensionSelectorPayload(
        UUID sessionId,
        long expiryGameTick,
        List<ResourceLocation> targets,
        ResourceLocation selected) implements CustomPacketPayload {
    public static final int MAX_TARGETS = 9;
    public static final Type<OpenDimensionSelectorPayload> TYPE =
            new Type<>(D4CPayloads.id("open_dimension_selector"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            OpenDimensionSelectorPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public OpenDimensionSelectorPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    UUID sessionId = buffer.readUUID();
                    long expiry = buffer.readVarLong();
                    int size = buffer.readVarInt();
                    if (size < 1 || size > MAX_TARGETS) {
                        throw new IllegalArgumentException(
                                "Invalid D4C dimension target count: "
                                        + size);
                    }
                    java.util.ArrayList<ResourceLocation> targets =
                            new java.util.ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        targets.add(buffer.readResourceLocation());
                    }
                    return new OpenDimensionSelectorPayload(
                            sessionId,
                            expiry,
                            targets,
                            buffer.readResourceLocation());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        OpenDimensionSelectorPayload payload) {
                    buffer.writeUUID(payload.sessionId());
                    buffer.writeVarLong(payload.expiryGameTick());
                    buffer.writeVarInt(payload.targets().size());
                    for (ResourceLocation target : payload.targets()) {
                        buffer.writeResourceLocation(target);
                    }
                    buffer.writeResourceLocation(payload.selected());
                }
            };

    public OpenDimensionSelectorPayload {
        targets = List.copyOf(targets);
        if (targets.isEmpty() || targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException(
                    "D4C dimension selector target count is out of bounds");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

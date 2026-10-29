package rotp.addons.d4c.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → user: living entities that the Saint Corpse eyes X-ray should outline (or stop outlining).
 * Mirrors the donor's {@code com.mrlang.rotp_d4c.ak.aa.aa.ac} entity-id list + glowing flag.
 */
public record SaintCorpseXrayGlowPayload(
        List<Integer> entityIds,
        boolean glowing) implements CustomPacketPayload {
    public static final int MAX_ENTITIES = 4096;
    public static final Type<SaintCorpseXrayGlowPayload> TYPE =
            new Type<>(D4CPayloads.id("saint_corpse_xray_glow"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            SaintCorpseXrayGlowPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SaintCorpseXrayGlowPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int size = buffer.readVarInt();
                    if (size < 0 || size > MAX_ENTITIES) {
                        throw new IllegalArgumentException(
                                "Invalid Saint Corpse X-ray entity count: "
                                        + size);
                    }
                    List<Integer> ids = new ArrayList<>(size);
                    for (int index = 0; index < size; index++) {
                        ids.add(buffer.readVarInt());
                    }
                    return new SaintCorpseXrayGlowPayload(
                            ids, buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SaintCorpseXrayGlowPayload payload) {
                    buffer.writeVarInt(payload.entityIds().size());
                    for (int id : payload.entityIds()) {
                        buffer.writeVarInt(id);
                    }
                    buffer.writeBoolean(payload.glowing());
                }
            };

    public SaintCorpseXrayGlowPayload {
        entityIds = List.copyOf(entityIds.size() > MAX_ENTITIES
                ? entityIds.subList(0, MAX_ENTITIES)
                : entityIds);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

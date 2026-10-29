package rotp.addons.kingcrimson.network;

import java.util.UUID;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonAfterimageClientController;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TimeEraseAfterimageSpawnPayload(
        int entityId,
        UUID entityUuid,
        int originEntityId,
        UUID originUuid,
        double x,
        double y,
        double z,
        float yRot,
        float xRot,
        int delayTicks,
        int lifespanTicks) implements CustomPacketPayload {
    public static final Type<TimeEraseAfterimageSpawnPayload> TYPE =
            new Type<>(KingCrimsonAddon.id(
                    "time_erase_afterimage_spawn"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TimeEraseAfterimageSpawnPayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            TimeEraseAfterimageSpawnPayload::encode,
                            TimeEraseAfterimageSpawnPayload::decode);

    public static TimeEraseAfterimageSpawnPayload from(
            KingCrimsonAfterimageEntity afterimage) {
        UUID origin = afterimage.originUuid();
        if (origin == null) {
            throw new IllegalStateException(
                    "Time Erase afterimage has no origin");
        }
        return new TimeEraseAfterimageSpawnPayload(
                afterimage.getId(),
                afterimage.getUUID(),
                afterimage.originEntityId(),
                origin,
                afterimage.getX(),
                afterimage.getY(),
                afterimage.getZ(),
                afterimage.getYRot(),
                afterimage.getXRot(),
                afterimage.delayTicks(),
                afterimage.lifespanTicks());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(entityId);
        buffer.writeUUID(entityUuid);
        buffer.writeVarInt(originEntityId);
        buffer.writeUUID(originUuid);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yRot);
        buffer.writeFloat(xRot);
        buffer.writeVarInt(delayTicks);
        buffer.writeVarInt(lifespanTicks);
    }

    private static TimeEraseAfterimageSpawnPayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new TimeEraseAfterimageSpawnPayload(
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public static void handle(
            TimeEraseAfterimageSpawnPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() ->
                KingCrimsonAfterimageClientController.spawn(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

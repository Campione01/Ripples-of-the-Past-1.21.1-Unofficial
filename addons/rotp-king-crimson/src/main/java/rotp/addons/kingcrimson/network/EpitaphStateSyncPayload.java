package rotp.addons.kingcrimson.network;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.state.EpitaphState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EpitaphStateSyncPayload(
        int entityId,
        UUID entityUuid,
        int stageOrdinal,
        int predictionOrdinal,
        long expiresAt,
        @Nullable UUID predictedAttackerUuid,
        @Nullable ResourceLocation predictedDamageType)
        implements CustomPacketPayload {
    public static final Type<EpitaphStateSyncPayload> TYPE =
            new Type<>(
                    KingCrimsonAddon.id(
                            "epitaph_state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            EpitaphStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public EpitaphStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    UUID entityUuid = buffer.readUUID();
                    int stage = buffer.readVarInt();
                    int prediction = buffer.readVarInt();
                    long expiresAt = buffer.readVarLong();
                    UUID attacker = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    ResourceLocation damageType =
                            buffer.readBoolean()
                                    ? buffer.readResourceLocation()
                                    : null;
                    return new EpitaphStateSyncPayload(
                            entityId,
                            entityUuid,
                            stage,
                            prediction,
                            expiresAt,
                            attacker,
                            damageType);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        EpitaphStateSyncPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeUUID(payload.entityUuid());
                    buffer.writeVarInt(payload.stageOrdinal());
                    buffer.writeVarInt(payload.predictionOrdinal());
                    buffer.writeVarLong(payload.expiresAt());
                    buffer.writeBoolean(
                            payload.predictedAttackerUuid() != null);
                    if (payload.predictedAttackerUuid() != null) {
                        buffer.writeUUID(
                                payload.predictedAttackerUuid());
                    }
                    buffer.writeBoolean(
                            payload.predictedDamageType() != null);
                    if (payload.predictedDamageType() != null) {
                        buffer.writeResourceLocation(
                                payload.predictedDamageType());
                    }
                }
            };

    public static EpitaphStateSyncPayload from(
            Player player, EpitaphState state) {
        return new EpitaphStateSyncPayload(
                player.getId(),
                player.getUUID(),
                state.stage().ordinal(),
                state.predictionKind().ordinal(),
                state.expiresAt(),
                state.predictedAttackerUuid(),
                state.predictedDamageType());
    }

    public static void handle(
            EpitaphStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity =
                localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof Player player
                && player.getUUID().equals(payload.entityUuid())) {
            KingCrimsonAddon.epitaphState(player)
                    .applyClientSnapshot(
                            payload.stageOrdinal(),
                            payload.predictionOrdinal(),
                            payload.expiresAt(),
                            payload.predictedAttackerUuid(),
                            payload.predictedDamageType());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

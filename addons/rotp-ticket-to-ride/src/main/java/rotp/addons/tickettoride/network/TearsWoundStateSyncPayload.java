package rotp.addons.tickettoride.network;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.state.TearsWoundState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TearsWoundStateSyncPayload(
        int entityId,
        boolean active,
        @Nullable UUID ownerUuid,
        long expiresAt) implements CustomPacketPayload {
    public static final Type<TearsWoundStateSyncPayload> TYPE =
            new Type<>(
                    TicketToRideAddon.id(
                            "tears_wound_state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TearsWoundStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TearsWoundStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    boolean active = buffer.readBoolean();
                    UUID ownerUuid = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    long expiresAt = buffer.readVarLong();
                    return new TearsWoundStateSyncPayload(
                            entityId,
                            active,
                            ownerUuid,
                            expiresAt);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        TearsWoundStateSyncPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.active());
                    buffer.writeBoolean(
                            payload.ownerUuid() != null);
                    if (payload.ownerUuid() != null) {
                        buffer.writeUUID(payload.ownerUuid());
                    }
                    buffer.writeVarLong(payload.expiresAt());
                }
            };

    public static TearsWoundStateSyncPayload from(
            LivingEntity target, TearsWoundState state) {
        return new TearsWoundStateSyncPayload(
                target.getId(),
                state.active(),
                state.ownerUuid(),
                state.expiresAt());
    }

    public static void handle(
            TearsWoundStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity =
                localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            TicketToRideAddon.woundState(living)
                    .applyClientSnapshot(
                            payload.active(),
                            payload.ownerUuid(),
                            payload.expiresAt());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package rotp.addons.madeinheaven.network;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.state.TimeAccelerationState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TimeAccelerationStatePayload(
        @Nullable UUID ownerUuid,
        boolean active,
        int phase) implements CustomPacketPayload {
    public static final Type<TimeAccelerationStatePayload> TYPE =
            new Type<>(MadeInHeavenAddon.id(
                    "time_acceleration_state"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TimeAccelerationStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TimeAccelerationStatePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    UUID owner = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    return new TimeAccelerationStatePayload(
                            owner,
                            buffer.readBoolean(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        TimeAccelerationStatePayload payload) {
                    buffer.writeBoolean(
                            payload.ownerUuid() != null);
                    if (payload.ownerUuid() != null) {
                        buffer.writeUUID(payload.ownerUuid());
                    }
                    buffer.writeBoolean(payload.active());
                    buffer.writeVarInt(payload.phase());
                }
            };

    public static TimeAccelerationStatePayload from(
            TimeAccelerationState state) {
        return new TimeAccelerationStatePayload(
                state.ownerUuid(),
                state.active(),
                state.phase());
    }

    public static void handle(
            TimeAccelerationStatePayload payload,
            IPayloadContext context) {
        MadeInHeavenAddon.accelerationState(
                        context.player().level())
                .applyClientSnapshot(
                        payload.ownerUuid(),
                        payload.active(),
                        payload.phase());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

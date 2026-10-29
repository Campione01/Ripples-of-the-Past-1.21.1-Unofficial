package rotp.addons.thelock.network;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.state.LockState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LockStateSyncPayload(
        int entityId,
        boolean active,
        @Nullable UUID ownerUuid,
        @Nullable UUID controllerUuid,
        float guilt,
        boolean chatCharged) implements CustomPacketPayload {
    public static final Type<LockStateSyncPayload> TYPE =
            new Type<>(TheLockAddon.id("lock_state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, LockStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LockStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    boolean active = buffer.readBoolean();
                    UUID ownerUuid = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    UUID controllerUuid = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    float guilt = buffer.readFloat();
                    boolean chatCharged = buffer.readBoolean();
                    return new LockStateSyncPayload(
                            entityId,
                            active,
                            ownerUuid,
                            controllerUuid,
                            guilt,
                            chatCharged);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        LockStateSyncPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.active());
                    buffer.writeBoolean(
                            payload.ownerUuid() != null);
                    if (payload.ownerUuid() != null) {
                        buffer.writeUUID(payload.ownerUuid());
                    }
                    buffer.writeBoolean(
                            payload.controllerUuid() != null);
                    if (payload.controllerUuid() != null) {
                        buffer.writeUUID(
                                payload.controllerUuid());
                    }
                    buffer.writeFloat(payload.guilt());
                    buffer.writeBoolean(payload.chatCharged());
                }
            };

    public static LockStateSyncPayload from(
            LivingEntity target, LockState state) {
        return new LockStateSyncPayload(
                target.getId(),
                state.active(),
                state.ownerUuid(),
                state.controllerUuid(),
                state.guilt(),
                state.chatCharged());
    }

    public static void handle(
            LockStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity =
                localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            TheLockAddon.lockState(living).applyClientSnapshot(
                    payload.active(),
                    payload.ownerUuid(),
                    payload.controllerUuid(),
                    payload.guilt(),
                    payload.chatCharged());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

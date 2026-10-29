package rotp.addons.heavensdoor.network;

import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BookStateSyncPayload(
        int entityId,
        boolean active,
        @Nullable UUID ownerUuid,
        @Nullable UUID targetUuid,
        @Nullable ResourceLocation dimension,
        long expiresAt,
        int pageCount,
        long generation,
        int bookAmplifier,
        @Nullable UUID pageOwnerUuid) implements CustomPacketPayload {
    public static final Type<BookStateSyncPayload> TYPE =
            new Type<>(HeavensDoorAddon.id("book_state_sync"));
    private static Consumer<BookStateSyncPayload> clientUpdateHandler = payload -> {};

    public static void installClientUpdateHandler(Consumer<BookStateSyncPayload> handler) {
        clientUpdateHandler = java.util.Objects.requireNonNull(handler);
    }

    public static final StreamCodec<
            RegistryFriendlyByteBuf, BookStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BookStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    int entityId = buffer.readVarInt();
                    boolean active = buffer.readBoolean();
                    UUID owner = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    UUID target = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    ResourceLocation dimension = buffer.readBoolean()
                            ? buffer.readResourceLocation()
                            : null;
                    long expiresAt = buffer.readVarLong();
                    int pages = buffer.readVarInt();
                    long generation = buffer.readVarLong();
                    int bookAmplifier = buffer.readVarInt();
                    UUID pageOwner = buffer.readBoolean() ? buffer.readUUID() : null;
                    return new BookStateSyncPayload(
                            entityId,
                            active,
                            owner,
                            target,
                            dimension,
                            expiresAt,
                            pages,
                            generation,
                            bookAmplifier,
                            pageOwner);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        BookStateSyncPayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.active());
                    buffer.writeBoolean(payload.ownerUuid() != null);
                    if (payload.ownerUuid() != null) {
                        buffer.writeUUID(payload.ownerUuid());
                    }
                    buffer.writeBoolean(payload.targetUuid() != null);
                    if (payload.targetUuid() != null) {
                        buffer.writeUUID(payload.targetUuid());
                    }
                    buffer.writeBoolean(payload.dimension() != null);
                    if (payload.dimension() != null) {
                        buffer.writeResourceLocation(
                                payload.dimension());
                    }
                    buffer.writeVarLong(payload.expiresAt());
                    buffer.writeVarInt(payload.pageCount());
                    buffer.writeVarLong(payload.generation());
                    buffer.writeVarInt(payload.bookAmplifier());
                    buffer.writeBoolean(payload.pageOwnerUuid() != null);
                    if (payload.pageOwnerUuid() != null) {
                        buffer.writeUUID(payload.pageOwnerUuid());
                    }
                }
            };

    public static BookStateSyncPayload from(
            LivingEntity target, BookState state) {
        return new BookStateSyncPayload(
                target.getId(),
                state.active(),
                state.ownerUuid(),
                state.targetUuid(),
                state.dimension(),
                state.expiresAt(),
                state.pageCount(),
                state.generation(),
                state.bookAmplifier(),
                state.pageOwnerUuid());
    }

    public static void handle(
            BookStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity =
                localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            HeavensDoorAddon.bookState(living).applyClientSnapshot(
                    payload.active(),
                    payload.ownerUuid(),
                    payload.targetUuid(),
                    payload.dimension(),
                    payload.expiresAt(),
                    payload.pageCount(),
                    payload.generation(),
                    payload.bookAmplifier(),
                    payload.pageOwnerUuid());
            clientUpdateHandler.accept(payload);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

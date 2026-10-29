package rotp.addons.killerqueenlion.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record KillerQueenStateSyncPayload(
        UUID ownerUuid,
        @Nullable UUID primaryTargetUuid,
        @Nullable ResourceLocation primaryTargetDimension,
        @Nullable BlockPos blockBombPos,
        @Nullable ResourceLocation blockBombDimension,
        List<BlockPos> bubbleBlockMarks,
        @Nullable ResourceLocation bubbleBlockDimension,
        @Nullable UUID sheerHeartUuid,
        @Nullable ResourceLocation sheerHeartDimension,
        boolean bitesTheDustMarked,
        @Nullable UUID bitesTheDustVictimUuid,
        @Nullable ResourceLocation bitesTheDustVictimDimension,
        long bitesTheDustMarkerTime,
        long bitesTheDustMarkerGameTime,
        @Nullable ResourceLocation bitesTheDustMarkerDimension,
        @Nullable UUID bitesTheDustMarkerEpoch,
        int linkedTimeStopId,
        @Nullable UUID bitesTheDustTriggerOwnerUuid,
        @Nullable UUID bitesTheDustTriggerVictimUuid,
        int bitesTheDustTriggerTicks) implements CustomPacketPayload {
    public static final Type<KillerQueenStateSyncPayload> TYPE =
            new Type<>(KillerQueenLionAddon.id("state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            KillerQueenStateSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public KillerQueenStateSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new KillerQueenStateSyncPayload(
                            buffer.readUUID(),
                            readOptionalUuid(buffer),
                            readOptionalLocation(buffer),
                            readOptionalBlockPos(buffer),
                            readOptionalLocation(buffer),
                            readBlockPosList(buffer),
                            readOptionalLocation(buffer),
                            readOptionalUuid(buffer),
                            readOptionalLocation(buffer),
                            buffer.readBoolean(),
                            readOptionalUuid(buffer),
                            readOptionalLocation(buffer),
                            buffer.readLong(),
                            buffer.readLong(),
                            readOptionalLocation(buffer),
                            readOptionalUuid(buffer),
                            buffer.readVarInt() - 1,
                            readOptionalUuid(buffer),
                            readOptionalUuid(buffer),
                            buffer.readVarInt() - 1);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        KillerQueenStateSyncPayload payload) {
                    buffer.writeUUID(payload.ownerUuid());
                    writeOptionalUuid(
                            buffer, payload.primaryTargetUuid());
                    writeOptionalLocation(
                            buffer,
                            payload.primaryTargetDimension());
                    writeOptionalBlockPos(
                            buffer, payload.blockBombPos());
                    writeOptionalLocation(
                            buffer,
                            payload.blockBombDimension());
                    writeBlockPosList(
                            buffer, payload.bubbleBlockMarks());
                    writeOptionalLocation(
                            buffer,
                            payload.bubbleBlockDimension());
                    writeOptionalUuid(
                            buffer, payload.sheerHeartUuid());
                    writeOptionalLocation(
                            buffer,
                            payload.sheerHeartDimension());
                    buffer.writeBoolean(
                            payload.bitesTheDustMarked());
                    writeOptionalUuid(
                            buffer,
                            payload.bitesTheDustVictimUuid());
                    writeOptionalLocation(
                            buffer,
                            payload.bitesTheDustVictimDimension());
                    buffer.writeLong(
                            payload.bitesTheDustMarkerTime());
                    buffer.writeLong(
                            payload.bitesTheDustMarkerGameTime());
                    writeOptionalLocation(
                            buffer,
                            payload.bitesTheDustMarkerDimension());
                    writeOptionalUuid(
                            buffer,
                            payload.bitesTheDustMarkerEpoch());
                    buffer.writeVarInt(
                            payload.linkedTimeStopId() + 1);
                    writeOptionalUuid(
                            buffer,
                            payload.bitesTheDustTriggerOwnerUuid());
                    writeOptionalUuid(
                            buffer,
                            payload.bitesTheDustTriggerVictimUuid());
                    buffer.writeVarInt(
                            payload.bitesTheDustTriggerTicks() + 1);
                }
            };

    public static KillerQueenStateSyncPayload from(
            Player owner, KillerQueenState state) {
        return new KillerQueenStateSyncPayload(
                owner.getUUID(),
                state.primaryTargetUuid(),
                state.primaryTargetDimension(),
                state.blockBombPos(),
                state.blockBombDimension(),
                state.bubbleBlockMarks(),
                state.bubbleBlockDimension(),
                state.sheerHeartUuid(),
                state.sheerHeartDimension(),
                state.hasBitesTheDustMarker(),
                state.bitesTheDustVictimUuid(),
                state.bitesTheDustVictimDimension(),
                state.bitesTheDustMarkerTime(),
                state.bitesTheDustMarkerGameTime(),
                state.bitesTheDustMarkerDimension(),
                state.bitesTheDustMarkerEpoch(),
                state.linkedTimeStopId(),
                state.bitesTheDustTriggerOwnerUuid(),
                state.bitesTheDustTriggerVictimUuid(),
                state.bitesTheDustTriggerTicks());
    }

    public static void handle(
            KillerQueenStateSyncPayload payload,
            IPayloadContext context) {
        Player localPlayer = context.player();
        Player owner = localPlayer.getUUID().equals(
                        payload.ownerUuid())
                ? localPlayer
                : localPlayer.level().getPlayerByUUID(
                        payload.ownerUuid());
        if (owner == null) {
            return;
        }
        KillerQueenLionAddon.state(owner).applyClientSnapshot(
                payload.primaryTargetUuid(),
                payload.primaryTargetDimension(),
                payload.blockBombPos(),
                payload.blockBombDimension(),
                payload.bubbleBlockMarks(),
                payload.bubbleBlockDimension(),
                payload.sheerHeartUuid(),
                payload.sheerHeartDimension(),
                payload.bitesTheDustMarked(),
                payload.bitesTheDustVictimUuid(),
                payload.bitesTheDustVictimDimension(),
                payload.bitesTheDustMarkerTime(),
                payload.bitesTheDustMarkerGameTime(),
                payload.bitesTheDustMarkerDimension(),
                payload.bitesTheDustMarkerEpoch(),
                payload.linkedTimeStopId(),
                payload.bitesTheDustTriggerOwnerUuid(),
                payload.bitesTheDustTriggerVictimUuid(),
                payload.bitesTheDustTriggerTicks());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    private static UUID readOptionalUuid(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static void writeOptionalUuid(
            RegistryFriendlyByteBuf buffer,
            @Nullable UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeUUID(value);
        }
    }

    @Nullable
    private static ResourceLocation readOptionalLocation(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? buffer.readResourceLocation()
                : null;
    }

    private static void writeOptionalLocation(
            RegistryFriendlyByteBuf buffer,
            @Nullable ResourceLocation value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeResourceLocation(value);
        }
    }

    @Nullable
    private static BlockPos readOptionalBlockPos(
            RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? buffer.readBlockPos()
                : null;
    }

    private static void writeOptionalBlockPos(
            RegistryFriendlyByteBuf buffer,
            @Nullable BlockPos value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeBlockPos(value);
        }
    }

    private static List<BlockPos> readBlockPosList(
            RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) {
            throw new IllegalArgumentException(
                    "Invalid Killer Queen bubble mark count: " + count);
        }
        List<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(buffer.readBlockPos());
        }
        return List.copyOf(positions);
    }

    private static void writeBlockPosList(
            RegistryFriendlyByteBuf buffer,
            List<BlockPos> positions) {
        List<BlockPos> safe = positions != null
                ? positions.stream().limit(256).toList()
                : List.of();
        buffer.writeVarInt(safe.size());
        for (BlockPos position : safe) {
            buffer.writeBlockPos(position);
        }
    }
}

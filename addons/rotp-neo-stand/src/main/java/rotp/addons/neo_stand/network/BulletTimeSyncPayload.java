package rotp.addons.neo_stand.network;

import java.util.Objects;
import java.util.UUID;

import rotp.addons.neo_stand.NeoStandAddon;
import rotp.addons.neo_stand.client.BulletTimeHud;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

public record BulletTimeSyncPayload(
        UUID owner,
        ResourceLocation dimension,
        BlockPos center,
        int radiusBlocks,
        int remainingTicks,
        float dilationFactor,
        boolean active) implements CustomPacketPayload {
    public static final String PROTOCOL_VERSION = "2";
    public static final int MAX_DIMENSION_ID_LENGTH = 256;
    public static final int MAX_RADIUS_BLOCKS = 32 * 16;
    public static final int MAX_REMAINING_TICKS = 20 * 60;

    public static final Type<BulletTimeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    NeoStandAddon.MOD_ID, "bullet_time_sync"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            BulletTimeSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public BulletTimeSyncPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    try {
                        return new BulletTimeSyncPayload(
                                buffer.readUUID(),
                                buffer.readResourceLocation(),
                                buffer.readBlockPos(),
                                buffer.readVarInt(),
                                buffer.readVarInt(),
                                buffer.readFloat(),
                                buffer.readBoolean());
                    }
                    catch (IllegalArgumentException error) {
                        throw new DecoderException(
                                "Invalid Bullet Time sync payload",
                                error);
                    }
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        BulletTimeSyncPayload payload) {
                    buffer.writeUUID(payload.owner());
                    buffer.writeResourceLocation(
                            payload.dimension());
                    buffer.writeBlockPos(payload.center());
                    buffer.writeVarInt(payload.radiusBlocks());
                    buffer.writeVarInt(payload.remainingTicks());
                    buffer.writeFloat(payload.dilationFactor());
                    buffer.writeBoolean(payload.active());
                }
            };

    public BulletTimeSyncPayload {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(center, "center");
        if (dimension.toString().length()
                > MAX_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Dimension id exceeds "
                            + MAX_DIMENSION_ID_LENGTH
                            + " characters");
        }
        if (!BlockPos.of(center.asLong()).equals(center)) {
            throw new IllegalArgumentException(
                    "Center cannot be represented by BlockPos wire format");
        }
        if (radiusBlocks <= 0
                || radiusBlocks > MAX_RADIUS_BLOCKS) {
            throw new IllegalArgumentException(
                    "Radius must be in [1, "
                            + MAX_RADIUS_BLOCKS + "]");
        }
        if (remainingTicks < 0
                || remainingTicks > MAX_REMAINING_TICKS) {
            throw new IllegalArgumentException(
                    "Remaining ticks must be in [0, "
                            + MAX_REMAINING_TICKS + "]");
        }
        if (active != (remainingTicks > 0)) {
            throw new IllegalArgumentException(
                    "Active state and remaining ticks disagree");
        }
        if (!Float.isFinite(dilationFactor)
                || dilationFactor <= 0.0F
                || dilationFactor > 1.0F) {
            throw new IllegalArgumentException(
                    "Dilation factor must be finite and in (0, 1]");
        }
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .optional()
                .executesOn(HandlerThread.MAIN)
                .playToClient(
                        TYPE,
                        STREAM_CODEC,
                        BulletTimeSyncPayload::handle);
    }

    private static void handle(
            BulletTimeSyncPayload payload,
            IPayloadContext context) {
        BulletTimeHud.accept(payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

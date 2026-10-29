package rotp.addons.gratefuldead.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlockAgingPayload(
        BlockPos position,
        float progress,
        int crackStage) implements CustomPacketPayload {
    public static final Type<BlockAgingPayload> TYPE =
            new Type<>(GratefulDeadAddon.id(
                    "aging_block_update"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, BlockAgingPayload>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public BlockAgingPayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new BlockAgingPayload(
                            buffer.readBlockPos(),
                            buffer.readFloat(),
                            buffer.readByte());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        BlockAgingPayload payload) {
                    buffer.writeBlockPos(payload.position());
                    buffer.writeFloat(payload.progress());
                    buffer.writeByte(payload.crackStage());
                }
            };

    public BlockAgingPayload {
        position = Objects.requireNonNull(position);
        progress = Float.isFinite(progress)
                ? Mth.clamp(progress, 0.0F, 1.0F)
                : 0.0F;
        crackStage = Mth.clamp(crackStage, -1, 9);
    }

    private static Consumer<BlockAgingPayload> clientHandler =
            payload -> {};

    public static void installClientHandler(
            Consumer<BlockAgingPayload> handler) {
        clientHandler = Objects.requireNonNull(handler);
    }

    public static void handle(
            BlockAgingPayload payload,
            IPayloadContext context) {
        context.enqueueWork(
                () -> clientHandler.accept(payload));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package rotp.addons.yellowtemperance.network;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ErosionVisualPayload(
        int targetId,
        int controllerId,
        boolean active) implements CustomPacketPayload {
    public static final Type<ErosionVisualPayload> TYPE =
            new Type<>(YellowTemperanceAddon.id("erosion_visual"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, ErosionVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ErosionVisualPayload::targetId,
                    ByteBufCodecs.INT,
                    ErosionVisualPayload::controllerId,
                    ByteBufCodecs.BOOL,
                    ErosionVisualPayload::active,
                    ErosionVisualPayload::new);

    public static void handle(
            ErosionVisualPayload payload,
            IPayloadContext context) {
        ErosionVisualBridge.apply(
                payload.targetId(),
                payload.controllerId(),
                payload.active());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

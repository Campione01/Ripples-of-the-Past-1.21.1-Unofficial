package rotp.addons.stickyfingers.network;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.ability.StickyFingersClimbZipperAbility;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Climb Zipper state: server to owner sets or clears the held
 * wall side; client to server only lets go (1.16
 * ClStopZipperClimbPacket).
 */
@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public record ZipperClimbPayload(int wall)
        implements CustomPacketPayload {
    public static final int STOP = -1;

    public static final Type<ZipperClimbPayload> TYPE =
            new Type<>(StickyFingersAddon.id("zipper_climb"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ZipperClimbPayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            ZipperClimbPayload::encode,
                            ZipperClimbPayload::decode);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(
                TYPE, STREAM_CODEC, ZipperClimbPayload::handle);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(wall + 1);
    }

    private static ZipperClimbPayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new ZipperClimbPayload(buffer.readVarInt() - 1);
    }

    private static void handle(
            ZipperClimbPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> StickyFingersClimbZipperAbility
                .handleSync(context.player(), payload.wall()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

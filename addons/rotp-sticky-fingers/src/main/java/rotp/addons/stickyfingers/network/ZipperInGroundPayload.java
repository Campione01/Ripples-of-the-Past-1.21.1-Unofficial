package rotp.addons.stickyfingers.network;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.state.ZipperInGround;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * In-ground flag, server to the player on change (1.16
 * EntityZipperCapSyncPacket isInGround).
 */
@EventBusSubscriber(
        modid = StickyFingersAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public record ZipperInGroundPayload(boolean inGround)
        implements CustomPacketPayload {
    public static final Type<ZipperInGroundPayload> TYPE =
            new Type<>(StickyFingersAddon.id("zipper_in_ground"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ZipperInGroundPayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            ZipperInGroundPayload::encode,
                            ZipperInGroundPayload::decode);

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(
                TYPE, STREAM_CODEC, ZipperInGroundPayload::handle);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(inGround);
    }

    private static ZipperInGroundPayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new ZipperInGroundPayload(buffer.readBoolean());
    }

    private static void handle(
            ZipperInGroundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ZipperInGround.handleSync(
                context.player(), payload.inGround()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

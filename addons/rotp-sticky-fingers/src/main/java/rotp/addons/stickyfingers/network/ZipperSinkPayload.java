package rotp.addons.stickyfingers.network;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.state.ZipperPhasing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = StickyFingersAddon.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record ZipperSinkPayload(boolean sinking) implements CustomPacketPayload {
    public static final Type<ZipperSinkPayload> TYPE =
            new Type<>(StickyFingersAddon.id("zipper_sink"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ZipperSinkPayload> STREAM_CODEC =
            StreamCodec.ofMember((payload, buffer) -> buffer.writeBoolean(payload.sinking()),
                    buffer -> new ZipperSinkPayload(buffer.readBoolean()));

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(TYPE, STREAM_CODEC, ZipperSinkPayload::handle);
    }

    private static void handle(ZipperSinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ZipperPhasing.setServerSinking(player, payload.sinking());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

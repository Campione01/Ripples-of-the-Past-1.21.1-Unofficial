package rotp.addons.tusk.network;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.state.TuskPortalOperations;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TuskPortalAcceleratePayload()
        implements CustomPacketPayload {
    public static final TuskPortalAcceleratePayload INSTANCE =
            new TuskPortalAcceleratePayload();
    public static final Type<TuskPortalAcceleratePayload> TYPE =
            new Type<>(TuskAddon.id("portal_accelerate"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TuskPortalAcceleratePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public TuskPortalAcceleratePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return INSTANCE;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        TuskPortalAcceleratePayload payload) {}
            };

    public static void handle(
            TuskPortalAcceleratePayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            TuskPortalOperations.get(player.server)
                    .accelerateInsidePortal(player);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

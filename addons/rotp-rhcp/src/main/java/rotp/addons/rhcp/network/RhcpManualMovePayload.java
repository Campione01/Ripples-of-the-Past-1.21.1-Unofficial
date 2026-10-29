package rotp.addons.rhcp.network;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.RhcpService;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Actual movement costs and chase cancellation are separate: an airborne jump can cancel without costing a jump.
@EventBusSubscriber(
        modid = RhcpAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD)
public record RhcpManualMovePayload(
        int standId,
        boolean walked,
        boolean jumped,
        boolean cancelChase) implements CustomPacketPayload {
    public static final Type<RhcpManualMovePayload> TYPE =
            new Type<>(RhcpAddon.id("manual_move"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, RhcpManualMovePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RhcpManualMovePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new RhcpManualMovePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RhcpManualMovePayload payload) {
                    buffer.writeVarInt(payload.standId());
                    buffer.writeBoolean(payload.walked());
                    buffer.writeBoolean(payload.jumped());
                    buffer.writeBoolean(payload.cancelChase());
                }
            };

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(RhcpAddon.NETWORK_PROTOCOL_VERSION).playToServer(
                TYPE,
                STREAM_CODEC,
                RhcpManualMovePayload::handle);
    }

    // 1.16 RHCPEntity.moveStandManually (walking 0.1 / 0.05 a tick) and
    // jumpFromGround / jumpInLiquid (1.25 / 0.625 a jump).
    public static void handle(
            RhcpManualMovePayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !RhcpAddon.isUser(player)) {
            return;
        }
        RhcpStandEntity stand = RhcpService.activeStand(player);
        if (stand == null || stand.getId() != payload.standId()) {
            return;
        }
        if (stand.isManuallyControlled()) {
            stand.cancelChaseFromManualInput(payload.walked() || payload.cancelChase(), payload.jumped());
        }
        if (payload.walked()) {
            RhcpService.consume(player, 0.1F, 0.05F);
        }
        if (payload.jumped()) {
            RhcpService.consume(player, 1.25F, 0.625F);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

package rotp.addons.ultimate_lifeform.network;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 1.16 ClDoubleShiftPressPacket, sent for an Ultimate Lifeform by ClDoubleShiftPressPacketMixin.
public record UltimateDoubleShiftPayload() implements CustomPacketPayload {
    public static final Type<UltimateDoubleShiftPayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("double_shift"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            UltimateDoubleShiftPayload> STREAM_CODEC =
            StreamCodec.unit(new UltimateDoubleShiftPayload());

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TYPE,
                STREAM_CODEC,
                UltimateDoubleShiftPayload::handle);
    }

    private static void handle(
            UltimateDoubleShiftPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PlayerPower.getPowerData(
                            player,
                            UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                    .ifPresent(data -> data.setDoubleShiftPress(player));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

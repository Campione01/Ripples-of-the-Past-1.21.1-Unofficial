package rotp.addons.ultimate_lifeform.network;

import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UltimateWallClimbInputPayload(
        float strafe,
        float forward,
        boolean jump) implements CustomPacketPayload {
    public static final Type<UltimateWallClimbInputPayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("wall_climb_input"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            UltimateWallClimbInputPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    UltimateWallClimbInputPayload::write,
                    UltimateWallClimbInputPayload::new);

    private UltimateWallClimbInputPayload(
            RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(strafe);
        buffer.writeFloat(forward);
        buffer.writeBoolean(jump);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TYPE,
                STREAM_CODEC,
                UltimateWallClimbInputPayload::handle);
    }

    private static void handle(
            UltimateWallClimbInputPayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || !Float.isFinite(payload.strafe)
                || !Float.isFinite(payload.forward)) {
            return;
        }
        UltimateLifeformData data = PlayerPower.getPowerData(
                        player,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        if (data != null) {
            data.acceptWallInput(
                    player,
                    payload.strafe,
                    payload.forward,
                    payload.jump);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

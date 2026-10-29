package rotp.addons.speedking.network;

import rotp.addons.speedking.SpeedKingAddon;
import rotp.addons.speedking.state.SpeedKingState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SpeedKingStatePayload(
        int entityId,
        boolean heatTrap,
        long heatTrapPos,
        String heatTrapDimension) implements CustomPacketPayload {
    public static final Type<SpeedKingStatePayload> TYPE =
            new Type<>(SpeedKingAddon.id("state"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, SpeedKingStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SpeedKingStatePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new SpeedKingStatePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readLong(),
                            buffer.readUtf());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SpeedKingStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.heatTrap());
                    buffer.writeLong(payload.heatTrapPos());
                    buffer.writeUtf(payload.heatTrapDimension());
                }
            };

    public static SpeedKingStatePayload from(Player player) {
        SpeedKingState state = SpeedKingAddon.state(player);
        return new SpeedKingStatePayload(
                player.getId(),
                state.hasHeatTrap(),
                state.heatTrapPos().asLong(),
                state.heatTrapDimension());
    }

    public static void handle(
            SpeedKingStatePayload payload,
            IPayloadContext context) {
        Entity entity = context.player().level()
                .getEntity(payload.entityId());
        if (entity instanceof Player player) {
            SpeedKingAddon.state(player).applyClientSnapshot(
                    payload.heatTrap(),
                    payload.heatTrapPos(),
                    payload.heatTrapDimension());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

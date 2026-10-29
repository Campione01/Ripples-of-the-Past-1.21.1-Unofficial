package rotp.addons.rhcp.network;

import rotp.addons.rhcp.RhcpAddon;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RhcpStatePayload(
        int entityId,
        float energy,
        int potency,
        boolean inConductor,
        long conductorPos,
        boolean blocking,
        boolean dodging,
        boolean boosting,
        int targetId) implements CustomPacketPayload {
    public static final Type<RhcpStatePayload> TYPE =
            new Type<>(RhcpAddon.id("state"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, RhcpStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RhcpStatePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new RhcpStatePayload(
                            buffer.readVarInt(),
                            buffer.readFloat(),
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readLong(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readVarInt());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        RhcpStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeFloat(payload.energy());
                    buffer.writeVarInt(payload.potency());
                    buffer.writeBoolean(payload.inConductor());
                    buffer.writeLong(payload.conductorPos());
                    buffer.writeBoolean(payload.blocking());
                    buffer.writeBoolean(payload.dodging());
                    buffer.writeBoolean(payload.boosting());
                    buffer.writeVarInt(payload.targetId());
                }
            };

    public static RhcpStatePayload from(Player player) {
        RhcpState state = RhcpAddon.state(player);
        return new RhcpStatePayload(
                player.getId(),
                state.energy(),
                state.potency(),
                state.inConductor(),
                state.conductorPos().asLong(),
                state.blocking(),
                state.dodging(),
                state.boosting(),
                state.targetId());
    }

    public static void handle(
            RhcpStatePayload payload,
            IPayloadContext context) {
        Entity entity = context.player().level()
                .getEntity(payload.entityId());
        if (entity instanceof Player player) {
            RhcpAddon.state(player).applyClientSnapshot(
                    payload.energy(),
                    payload.potency(),
                    payload.inConductor(),
                    payload.conductorPos(),
                    payload.blocking(),
                    payload.dodging(),
                    payload.boosting(),
                    payload.targetId());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}


package rotp.addons.achtungbaby.network;

import rotp.addons.achtungbaby.AchtungBabyAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AchtungBabyStatePayload(
        int entityId,
        boolean perfectInvisible,
        boolean ghostMode) implements CustomPacketPayload {
    public static final Type<AchtungBabyStatePayload> TYPE =
            new Type<>(AchtungBabyAddon.id("state"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, AchtungBabyStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public AchtungBabyStatePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new AchtungBabyStatePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean());
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        AchtungBabyStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.perfectInvisible());
                    buffer.writeBoolean(payload.ghostMode());
                }
            };

    public static AchtungBabyStatePayload from(Player player) {
        var state = AchtungBabyAddon.state(player);
        return new AchtungBabyStatePayload(
                player.getId(),
                state.perfectInvisible(),
                state.ghostMode());
    }

    public static void handle(
            AchtungBabyStatePayload payload,
            IPayloadContext context) {
        Entity entity = context.player().level()
                .getEntity(payload.entityId());
        if (entity instanceof Player player) {
            AchtungBabyAddon.state(player).applyClientSnapshot(
                    payload.perfectInvisible(),
                    payload.ghostMode());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

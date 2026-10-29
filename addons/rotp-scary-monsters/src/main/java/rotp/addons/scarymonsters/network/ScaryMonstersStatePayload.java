package rotp.addons.scarymonsters.network;

import java.util.UUID;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ScaryMonstersStatePayload(
        int entityId,
        boolean dynamicVision,
        boolean dragonForm,
        boolean dinosaurForm,
        UUID controlledDinosaur) implements CustomPacketPayload {
    public static final Type<ScaryMonstersStatePayload> TYPE =
            new Type<>(ScaryMonstersAddon.id("state"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf, ScaryMonstersStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ScaryMonstersStatePayload decode(
                        RegistryFriendlyByteBuf buffer) {
                    return new ScaryMonstersStatePayload(
                            buffer.readVarInt(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readBoolean(),
                            buffer.readBoolean() ? buffer.readUUID() : null);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        ScaryMonstersStatePayload payload) {
                    buffer.writeVarInt(payload.entityId());
                    buffer.writeBoolean(payload.dynamicVision());
                    buffer.writeBoolean(payload.dragonForm());
                    buffer.writeBoolean(payload.dinosaurForm());
                    buffer.writeBoolean(payload.controlledDinosaur() != null);
                    if (payload.controlledDinosaur() != null) {
                        buffer.writeUUID(payload.controlledDinosaur());
                    }
                }
            };

    public static ScaryMonstersStatePayload from(Player player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        return new ScaryMonstersStatePayload(
                player.getId(),
                state.dynamicVision(),
                state.dragonForm(),
                state.dinosaurForm(),
                state.controlledDinosaur());
    }

    public static void handle(
            ScaryMonstersStatePayload payload,
            IPayloadContext context) {
        Entity entity = context.player().level()
                .getEntity(payload.entityId());
        if (entity instanceof Player player) {
            ScaryMonstersAddon.state(player).applyClientSnapshot(
                    payload.dynamicVision(),
                    payload.dragonForm(),
                    payload.dinosaurForm(),
                    payload.controlledDinosaur());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

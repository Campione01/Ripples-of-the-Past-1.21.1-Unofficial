package rotp.addons.tusk.network;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TuskNailStateSyncPayload(
        int playerEntityId,
        int nails,
        int regenTicksRemaining,
        boolean infiniteRotationCharged) implements CustomPacketPayload {
    public static final Type<TuskNailStateSyncPayload> TYPE =
            new Type<>(TuskAddon.id("nail_state_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, TuskNailStateSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    TuskNailStateSyncPayload::playerEntityId,
                    ByteBufCodecs.INT,
                    TuskNailStateSyncPayload::nails,
                    ByteBufCodecs.INT,
                    TuskNailStateSyncPayload::regenTicksRemaining,
                    ByteBufCodecs.BOOL,
                    TuskNailStateSyncPayload::infiniteRotationCharged,
                    TuskNailStateSyncPayload::new);

    public static void handle(
            TuskNailStateSyncPayload payload, IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity = localPlayer.level().getEntity(payload.playerEntityId());
        if (entity instanceof Player player) {
            TuskAddon.nailState(player).applySnapshot(
                    payload.nails(),
                    payload.regenTicksRemaining(),
                    payload.infiniteRotationCharged());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

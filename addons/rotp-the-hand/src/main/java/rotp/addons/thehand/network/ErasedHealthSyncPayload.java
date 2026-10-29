package rotp.addons.thehand.network;

import rotp.addons.thehand.TheHandAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ErasedHealthSyncPayload(
        int entityId,
        float erasedHealth,
        long expiresAtGameTime) implements CustomPacketPayload {
    public static final Type<ErasedHealthSyncPayload> TYPE =
            new Type<>(TheHandAddon.id("erased_health_sync"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf, ErasedHealthSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ErasedHealthSyncPayload::entityId,
                    ByteBufCodecs.FLOAT,
                    ErasedHealthSyncPayload::erasedHealth,
                    ByteBufCodecs.VAR_LONG,
                    ErasedHealthSyncPayload::expiresAtGameTime,
                    ErasedHealthSyncPayload::new);

    public static void handle(
            ErasedHealthSyncPayload payload, IPayloadContext context) {
        Player localPlayer = context.player();
        Entity entity = localPlayer.level().getEntity(payload.entityId());
        if (entity instanceof LivingEntity living) {
            TheHandAddon.erasedHealthState(living).applyClientSnapshot(
                    payload.erasedHealth(),
                    payload.expiresAtGameTime());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

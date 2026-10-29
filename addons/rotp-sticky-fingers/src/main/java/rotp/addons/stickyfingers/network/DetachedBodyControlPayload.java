package rotp.addons.stickyfingers.network;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.state.DetachedPartType;
import rotp.addons.stickyfingers.state.ZipperCombatData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DetachedBodyControlPayload(
        int action) implements CustomPacketPayload {
    public static final int RESPAWN_FROM_HEAD = 0;

    public static final Type<DetachedBodyControlPayload> TYPE =
            new Type<>(StickyFingersAddon.id(
                    "detached_body_control"));
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            DetachedBodyControlPayload> STREAM_CODEC =
                    StreamCodec.ofMember(
                            DetachedBodyControlPayload::encode,
                            DetachedBodyControlPayload::decode);

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                TYPE, STREAM_CODEC,
                DetachedBodyControlPayload::handle);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(action);
    }

    private static DetachedBodyControlPayload decode(
            RegistryFriendlyByteBuf buffer) {
        return new DetachedBodyControlPayload(
                buffer.readVarInt());
    }

    private static void handle(
            DetachedBodyControlPayload payload,
            IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player()
                    instanceof ServerPlayer player)) {
                return;
            }
            if (payload.action() == RESPAWN_FROM_HEAD) {
                respawnFromDetachedHead(player);
            }
        });
    }

    private static void respawnFromDetachedHead(
            ServerPlayer player) {
        if (!player.isAlive()
                || !ZipperCombatData.get(player.serverLevel())
                        .partsFor(player.getUUID())
                        .contains(DetachedPartType.HEAD)) {
            return;
        }
        player.invulnerableTime = 0;
        player.removeEffect(MobEffects.FIRE_RESISTANCE);
        player.hurt(
                player.damageSources().genericKill(),
                Float.MAX_VALUE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

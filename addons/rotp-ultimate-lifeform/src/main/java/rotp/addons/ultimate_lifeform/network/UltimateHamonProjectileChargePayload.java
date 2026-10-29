package rotp.addons.ultimate_lifeform.network;

import rotp.addons.ultimate_lifeform.UltimateHamonProjectileCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 UltimateHamonEntityChargePacket.projectileCharge: tells a client which projectiles spark. */
public record UltimateHamonProjectileChargePayload(
        int entityId,
        boolean hasCharge) implements CustomPacketPayload {
    public static final Type<UltimateHamonProjectileChargePayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("hamon_projectile_charge"));
    public static final StreamCodec<ByteBuf, UltimateHamonProjectileChargePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    UltimateHamonProjectileChargePayload::entityId,
                    ByteBufCodecs.BOOL,
                    UltimateHamonProjectileChargePayload::hasCharge,
                    UltimateHamonProjectileChargePayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE,
                STREAM_CODEC,
                UltimateHamonProjectileChargePayload::handle);
    }

    private static void handle(
            UltimateHamonProjectileChargePayload payload,
            IPayloadContext context) {
        if (context.player().level().getEntity(payload.entityId)
                instanceof Projectile projectile) {
            UltimateHamonProjectileCharge.setClientCharge(
                    projectile, payload.hasCharge);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

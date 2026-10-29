package rotp.addons.ultimate_lifeform.network;

import rotp.addons.ultimate_lifeform.UltimateHamonEntityCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 1.16 UltimateHamonEntityChargePacket.entityCharge: tells a client which entities carry an Ultimate charge. */
public record UltimateHamonEntityChargePayload(
        int entityId,
        boolean hasCharge) implements CustomPacketPayload {
    public static final Type<UltimateHamonEntityChargePayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("hamon_entity_charge"));
    public static final StreamCodec<ByteBuf, UltimateHamonEntityChargePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    UltimateHamonEntityChargePayload::entityId,
                    ByteBufCodecs.BOOL,
                    UltimateHamonEntityChargePayload::hasCharge,
                    UltimateHamonEntityChargePayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE,
                STREAM_CODEC,
                UltimateHamonEntityChargePayload::handle);
    }

    private static void handle(
            UltimateHamonEntityChargePayload payload,
            IPayloadContext context) {
        Entity entity = context.player().level().getEntity(payload.entityId);
        if (entity != null) {
            UltimateHamonEntityCharge.get(entity).setClientHasCharge(payload.hasCharge);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

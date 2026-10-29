package rotp.addons.ultimate_lifeform.network;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformClientEffects;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 1.16 CustomParticles.createUltimateHamonSparkParticles(entity, ...) drew the Ultimate Hamon Overdrive's and the Hamon
 * Barrage's sparks on the client as particles that moved with the struck entity from the point they were made at: the
 * point is the target's getX(1.0), getY(random) and getZ(1.0), every particle of the burst at that one point.
 */
public record UltimateSparkBurstPayload(
        int entityId,
        double x,
        double y,
        double z,
        int count) implements CustomPacketPayload {
    public static final Type<UltimateSparkBurstPayload> TYPE =
            new Type<>(UltimateLifeformAddon.id("spark_burst"));
    public static final StreamCodec<ByteBuf, UltimateSparkBurstPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    UltimateSparkBurstPayload::entityId,
                    ByteBufCodecs.DOUBLE,
                    UltimateSparkBurstPayload::x,
                    ByteBufCodecs.DOUBLE,
                    UltimateSparkBurstPayload::y,
                    ByteBufCodecs.DOUBLE,
                    UltimateSparkBurstPayload::z,
                    ByteBufCodecs.VAR_INT,
                    UltimateSparkBurstPayload::count,
                    UltimateSparkBurstPayload::new);

    // the donor's point on the struck entity: x and z one bounding-box width out, y a random height
    public static UltimateSparkBurstPayload at(Entity target, int count) {
        return new UltimateSparkBurstPayload(
                target.getId(),
                target.getX(1.0D),
                target.getY(Math.random()),
                target.getZ(1.0D),
                count);
    }

    public static void sendToUser(
            ServerPlayer user, Entity target, int count) {
        PacketDistributor.sendToPlayer(user, at(target, count));
    }

    // the user's client and every client tracking the user ran the 1.16 strike instance
    public static void sendToUserAndTrackers(
            Entity user, Entity target, int count) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                user, at(target, count));
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TYPE,
                STREAM_CODEC,
                UltimateSparkBurstPayload::handle);
    }

    private static void handle(
            UltimateSparkBurstPayload payload,
            IPayloadContext context) {
        UltimateLifeformClientEffects.sparksFollowing(
                payload.entityId, payload.x, payload.y, payload.z,
                payload.count);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

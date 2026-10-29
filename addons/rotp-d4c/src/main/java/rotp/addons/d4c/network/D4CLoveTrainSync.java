package rotp.addons.d4c.network;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import rotp.addons.d4c.network.LoveTrainPresentationPayload.Signal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public final class D4CLoveTrainSync {
    private D4CLoveTrainSync() {}

    public static void start(LivingEntity owner) {
        send(owner, Signal.START);
    }

    public static void commit(LivingEntity owner) {
        send(owner, Signal.COMMIT);
    }

    public static void cancel(LivingEntity owner) {
        send(owner, Signal.CANCEL);
    }

    private static void send(
            LivingEntity owner,
            Signal signal) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        LoveTrainPresentationPayload payload =
                new LoveTrainPresentationPayload(
                        owner.getUUID(), signal);
        Set<ServerPlayer> trackingPlayers = new HashSet<>(
                level.getChunkSource()
                        .chunkMap
                        .getPlayers(owner.chunkPosition(), false));
        Set<ServerPlayer> recipients =
                new LinkedHashSet<>(trackingPlayers);
        if (owner instanceof ServerPlayer self) {
            recipients.add(self);
        }

        int baseTrackingDistance =
                owner.getType().clientTrackingRange() * 16;
        double trackingDistance = level.getServer()
                .getScaledTrackingDistance(baseTrackingDistance);
        double maxDistanceSquared =
                trackingDistance * trackingDistance;
        for (ServerPlayer observer : recipients) {
            boolean ownerSelf = owner == observer;
            double deltaX = observer.getX() - owner.getX();
            double deltaZ = observer.getZ() - owner.getZ();
            boolean hasChannel = observer.connection != null
                    && observer.connection.hasChannel(
                            LoveTrainPresentationPayload.TYPE);
            if (D4CLoveTrainRecipientPolicy.shouldSend(
                    observer.level() == level,
                    ownerSelf,
                    trackingPlayers.contains(observer),
                    ownerSelf || owner.broadcastToPlayer(observer),
                    deltaX * deltaX + deltaZ * deltaZ,
                    maxDistanceSquared,
                    hasChannel)) {
                PacketDistributor.sendToPlayer(observer, payload);
            }
        }
    }
}

package rotp.addons.sexpistols.network;

import java.util.Objects;
import java.util.function.Consumer;

import rotp.addons.sexpistols.content.revolver.state.RevolverSpecialChambers.ShotType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class RevolverVisualPayloads {
    public static final String NETWORK_PROTOCOL_VERSION = "visual-3";

    private static Consumer<RevolverMuzzleFlashPayload>
            clientMuzzleHandler = payload -> {};
    private static Consumer<RevolverCasingEjectPayload>
            clientCasingHandler = payload -> {};
    private static Consumer<SexPistolsKickMuzzleFlashPayload>
            clientKickHandler = payload -> {};
    private static Consumer<RevolverBulletHolePayload>
            clientBulletHoleHandler = payload -> {};

    private RevolverVisualPayloads() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        var registrar = event
                .registrar(NETWORK_PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN);
        registrar.playToClient(
                RevolverMuzzleFlashPayload.TYPE,
                RevolverMuzzleFlashPayload.STREAM_CODEC,
                (payload, context) ->
                        clientMuzzleHandler.accept(payload));
        registrar.playToClient(
                RevolverCasingEjectPayload.TYPE,
                RevolverCasingEjectPayload.STREAM_CODEC,
                (payload, context) ->
                        clientCasingHandler.accept(payload));
        registrar.playToClient(
                SexPistolsKickMuzzleFlashPayload.TYPE,
                SexPistolsKickMuzzleFlashPayload.STREAM_CODEC,
                (payload, context) ->
                        clientKickHandler.accept(payload));
        registrar.playToClient(
                RevolverBulletHolePayload.TYPE,
                RevolverBulletHolePayload.STREAM_CODEC,
                (payload, context) ->
                        clientBulletHoleHandler.accept(payload));
    }

    public static void installClientHandlers(
            Consumer<RevolverMuzzleFlashPayload> muzzleHandler,
            Consumer<RevolverCasingEjectPayload> casingHandler,
            Consumer<SexPistolsKickMuzzleFlashPayload> kickHandler,
            Consumer<RevolverBulletHolePayload> bulletHoleHandler) {
        clientMuzzleHandler = Objects.requireNonNull(muzzleHandler);
        clientCasingHandler = Objects.requireNonNull(casingHandler);
        clientKickHandler = Objects.requireNonNull(kickHandler);
        clientBulletHoleHandler = Objects.requireNonNull(bulletHoleHandler);
    }

    public static void sendMuzzle(
            Entity shooter, boolean aiming, float random) {
        sendMuzzle(shooter, aiming, random, true);
    }

    public static void sendMuzzle(
            Entity shooter,
            boolean aiming,
            float random,
            ShotType shotType) {
        sendMuzzle(
                shooter,
                aiming,
                random,
                Objects.requireNonNull(shotType)
                        != ShotType.ENCIRCLEMENT);
    }

    private static void sendMuzzle(
            Entity shooter,
            boolean aiming,
            float random,
            boolean applyRecoil) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                shooter,
                new RevolverMuzzleFlashPayload(
                        shooter.getId(),
                        aiming,
                        random,
                        applyRecoil));
    }

    public static void sendKickFlash(
            Entity member,
            boolean bulletAnchor,
            float random,
            boolean critical) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                member,
                new SexPistolsKickMuzzleFlashPayload(
                        member.getId(),
                        bulletAnchor,
                        random,
                        critical));
    }

    public static void sendBulletHole(
            Entity bullet,
            Vec3 position,
            Direction direction,
            BlockPos blockPos) {
        if (bullet.level() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersNear(
                    level,
                    null,
                    position.x,
                    position.y,
                    position.z,
                    32.0D,
                    new RevolverBulletHolePayload(
                            position,
                            direction,
                            blockPos));
        }
    }
}

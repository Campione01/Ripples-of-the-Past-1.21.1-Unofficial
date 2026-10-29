package rotp.addons.sexpistols.content.revolver.casing;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import rotp.addons.sexpistols.network.RevolverCasingEjectPayload;
import rotp.addons.sexpistols.registry.SexPistolsItems;
import rotp.addons.sexpistols.registry.SexPistolsSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RevolverCasings {
    private static final int MAX_PENDING_TICKS = 80;
    private static final Map<MinecraftServer, List<PendingCasing>>
            PENDING_BY_SERVER = new IdentityHashMap<>();

    private RevolverCasings() {}

    public static void eject(ServerPlayer player, int count) {
        List<CasingEjectionSpec> specs =
                CasingEjectionFactory.create(player, count);
        if (specs.isEmpty()) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<PendingCasing> pending = PENDING_BY_SERVER
                .computeIfAbsent(server, ignored -> new ArrayList<>());
        UUID ownerId = player.getUUID();
        for (CasingEjectionSpec spec : specs) {
            pending.add(new PendingCasing(level, ownerId, spec));
        }
        broadcastCasingSpecs(player, specs);
    }

    static void tick(MinecraftServer server) {
        deliverCompensations(server);
        List<PendingCasing> pending = PENDING_BY_SERVER.get(server);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        Iterator<PendingCasing> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingCasing casing = iterator.next();
            if (casing.level.getGameTime() <= casing.spawnGameTime) {
                continue;
            }
            casing.elapsedTicks++;
            if (!casing.state.removed()) {
                boolean stopped = CasingWorldPhysics.isTimeStopped(
                        casing.level, casing.state);
                CasingFlightState.TickResult result = casing.state.tick(
                        stopped, casing.collisionResolver);
                if (result.landed()) {
                    broadcastLandingSound(casing);
                }
            }
            if (casing.state.removed()
                    && spawnAtAuthoritativePosition(casing)) {
                iterator.remove();
                continue;
            }
            if (casing.elapsedTicks >= MAX_PENDING_TICKS) {
                settleAfterTimeout(casing);
                iterator.remove();
            }
        }
        if (pending.isEmpty()) {
            PENDING_BY_SERVER.remove(server);
        }
    }

    static boolean flushLevel(ServerLevel level) {
        MinecraftServer server = level.getServer();
        List<PendingCasing> pending = PENDING_BY_SERVER.get(server);
        if (pending == null) {
            return false;
        }
        boolean credited = false;
        Iterator<PendingCasing> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingCasing casing = iterator.next();
            if (casing.level == level) {
                compensate(casing);
                credited = true;
                iterator.remove();
            }
        }
        if (pending.isEmpty()) {
            PENDING_BY_SERVER.remove(server);
        }
        return credited;
    }

    static void flushServer(MinecraftServer server) {
        List<PendingCasing> pending = PENDING_BY_SERVER.remove(server);
        if (pending == null) {
            return;
        }
        pending.forEach(RevolverCasings::compensate);
    }

    static void deliverCompensation(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            CasingCompensationData.get(server).deliver(player);
        }
    }

    private static void deliverCompensations(MinecraftServer server) {
        CasingCompensationData data = CasingCompensationData.get(server);
        if (!data.hasCredits()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            data.deliver(player);
        }
    }

    private static void settleAfterTimeout(PendingCasing casing) {
        if (spawnAtAuthoritativePosition(casing)
                || spawnItem(casing.level, fallbackPosition(casing))) {
            return;
        }
        compensate(casing);
    }

    private static void compensate(PendingCasing casing) {
        CasingCompensationData.get(casing.level.getServer())
                .credit(casing.ownerId, 1L);
    }

    private static void broadcastCasingSpecs(
            ServerPlayer shooter,
            List<CasingEjectionSpec> specs) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                shooter,
                new RevolverCasingEjectPayload(
                        shooter.getId(), specs));
    }

    private static void broadcastLandingSound(PendingCasing casing) {
        CasingEjectionSpec spec = casing.state.spec();
        casing.level.playSound(
                null,
                casing.state.x(),
                casing.state.y(),
                casing.state.z(),
                landingSound(spec.landingSound()),
                SoundSource.PLAYERS,
                0.34F,
                spec.landingPitch());
    }

    private static SoundEvent landingSound(int index) {
        return switch (index) {
            case 0 -> SexPistolsSounds.SHELL_CASTING_1.get();
            case 1 -> SexPistolsSounds.SHELL_CASTING_2.get();
            case 2 -> SexPistolsSounds.SHELL_CASTING_3.get();
            default -> SexPistolsSounds.SHELL_CASTING_4.get();
        };
    }

    private static boolean spawnAtAuthoritativePosition(
            PendingCasing casing) {
        Vec3 position = new Vec3(
                casing.state.x(),
                casing.state.y(),
                casing.state.z());
        if (!validPosition(casing.level, position)) {
            return false;
        }
        return spawnItem(casing.level, position);
    }

    private static Vec3 fallbackPosition(PendingCasing casing) {
        ServerPlayer owner = casing.level.getServer()
                .getPlayerList()
                .getPlayer(casing.ownerId);
        if (owner != null && owner.serverLevel() == casing.level) {
            return owner.position().add(
                    0.0D, owner.getEyeHeight() - 0.25D, 0.0D);
        }
        return casing.fallbackPosition;
    }

    private static boolean spawnItem(
            ServerLevel level, Vec3 position) {
        if (!validPosition(level, position)) {
            return false;
        }
        ItemEntity item = new ItemEntity(
                level,
                position.x,
                position.y,
                position.z,
                new ItemStack(SexPistolsItems.REVOLVER_CASING.get()));
        item.setPickUpDelay(25);
        item.setDeltaMovement(0.0D, 0.025D, 0.0D);
        return level.addFreshEntity(item);
    }

    private static boolean validPosition(
            ServerLevel level, Vec3 position) {
        return Double.isFinite(position.x)
                && Double.isFinite(position.y)
                && Double.isFinite(position.z)
                && level.isInWorldBounds(BlockPos.containing(position));
    }

    private static final class PendingCasing {
        private final ServerLevel level;
        private final UUID ownerId;
        private final Vec3 fallbackPosition;
        private final long spawnGameTime;
        private final CasingFlightState state;
        private final CasingFlightState.CollisionResolver
                collisionResolver;
        private int elapsedTicks;

        private PendingCasing(
                ServerLevel level,
                UUID ownerId,
                CasingEjectionSpec spec) {
            this.level = level;
            this.ownerId = ownerId;
            this.fallbackPosition = new Vec3(
                    spec.x(), spec.y(), spec.z());
            this.spawnGameTime = spec.spawnGameTime();
            this.state = new CasingFlightState(spec);
            this.collisionResolver = CasingWorldPhysics.resolver(level);
        }
    }
}

package rotp.addons.sexpistols.network;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class SexPistolsConfigSync {
    public static final String NETWORK_PROTOCOL_VERSION =
            "sex-pistols-config-2";

    private static final AtomicReference<SexPistolsConfigSyncPayload>
            SERVER_SNAPSHOT = new AtomicReference<>(defaults());
    private static volatile ClientHandler clientHandler =
            (payload, connection) -> {};

    private SexPistolsConfigSync() {}

    public static void register(
            RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN)
                .playToClient(
                        SexPistolsConfigSyncPayload.TYPE,
                        SexPistolsConfigSyncPayload.STREAM_CODEC,
                        (payload, context) -> clientHandler.accept(
                                payload, context.connection()));
    }

    public static void installClientHandler(
            ClientHandler handler) {
        clientHandler = Objects.requireNonNull(handler);
    }

    @FunctionalInterface
    public interface ClientHandler {
        void accept(
                SexPistolsConfigSyncPayload payload,
                Connection connection);
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(
                player, SERVER_SNAPSHOT.get());
    }

    public static void onConfigLoading(
            ModConfigEvent.Loading event) {
        refresh(event.getConfig());
    }

    public static void onConfigReloading(
            ModConfigEvent.Reloading event) {
        if (!refresh(event.getConfig())) {
            return;
        }
        MinecraftServer server =
                ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        SexPistolsConfigSyncPayload snapshot =
                SERVER_SNAPSHOT.get();
        server.execute(() -> {
            if (ServerLifecycleHooks.getCurrentServer() == server) {
                PacketDistributor.sendToAllPlayers(snapshot);
            }
        });
    }

    public static void onConfigUnloading(
            ModConfigEvent.Unloading event) {
        if (owns(event.getConfig())) {
            SERVER_SNAPSHOT.set(defaults());
        }
    }

    private static boolean refresh(ModConfig config) {
        if (!owns(config)) {
            return false;
        }
        SERVER_SNAPSHOT.set(new SexPistolsConfigSyncPayload(
                SexPistolsConfig.bulletDamageMultiplier(),
                SexPistolsConfig.bulletDestroysBlocks(),
                Math.toIntExact(
                        SexPistolsConfig.blockDamageMemoryTicks()),
                SexPistolsConfig.stableDamageEndDistance(),
                SexPistolsConfig.closeDamageDropDistance(),
                SexPistolsConfig.minDistanceDamage(),
                SexPistolsConfig.maxRicochetCount(),
                SexPistolsConfig.targetSearchRange(),
                SexPistolsConfig.transferAssistRange(),
                SexPistolsConfig.reviveTicks(),
                SexPistolsConfig.hungerEnabled(),
                SexPistolsConfig.hungerMinTicks(),
                SexPistolsConfig.hungerExtraTicks()));
        return true;
    }

    private static boolean owns(ModConfig config) {
        return config != null
                && config.getSpec() == SexPistolsConfig.SPEC;
    }

    private static SexPistolsConfigSyncPayload defaults() {
        return new SexPistolsConfigSyncPayload(
                SexPistolsConfig.DEFAULT_BULLET_DAMAGE_MULTIPLIER,
                SexPistolsConfig.DEFAULT_BULLET_DESTROYS_BLOCKS,
                SexPistolsConfig.DEFAULT_BLOCK_DAMAGE_MEMORY_TICKS,
                SexPistolsConfig.DEFAULT_STABLE_DAMAGE_END_DISTANCE,
                SexPistolsConfig.DEFAULT_CLOSE_DAMAGE_DROP_DISTANCE,
                SexPistolsConfig.DEFAULT_MIN_DISTANCE_DAMAGE,
                SexPistolsConfig.DEFAULT_MAX_RICOCHET_COUNT,
                SexPistolsConfig.DEFAULT_TARGET_SEARCH_RANGE,
                SexPistolsConfig.DEFAULT_TRANSFER_ASSIST_RANGE,
                SexPistolsConfig.DEFAULT_SEX_PISTOLS_REVIVE_TICKS,
                SexPistolsConfig.DEFAULT_SEX_PISTOLS_HUNGER_ENABLED,
                SexPistolsConfig.DEFAULT_SEX_PISTOLS_HUNGER_MIN_TICKS,
                SexPistolsConfig.DEFAULT_SEX_PISTOLS_HUNGER_EXTRA_TICKS);
    }
}

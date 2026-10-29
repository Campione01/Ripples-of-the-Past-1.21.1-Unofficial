package rotp.addons.sexpistols.client;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.network.SexPistolsConfigSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;

public final class SexPistolsClientConfigSnapshot {
    private static final Object SESSION_LOCK = new Object();
    private static final AtomicReference<Snapshot> LOCAL =
            new AtomicReference<>(defaults());
    private static final AtomicReference<Snapshot> ACTIVE =
            new AtomicReference<>(LOCAL.get());
    private static final AtomicReference<ClientSession> SESSION =
            new AtomicReference<>(ClientSession.disconnected());
    private static final AtomicLong SESSION_GENERATION = new AtomicLong();
    private static final AtomicLong CALLBACKS_RECEIVED = new AtomicLong();
    private static final AtomicLong CALLBACKS_APPLIED = new AtomicLong();
    private static final AtomicLong CALLBACKS_REJECTED_DISCONNECTED =
            new AtomicLong();
    private static final AtomicLong CALLBACKS_REJECTED_STALE_SESSION =
            new AtomicLong();

    private SexPistolsClientConfigSnapshot() {}

    public static Snapshot snapshot() {
        return ACTIVE.get();
    }

    public static void beginSession(Connection connection) {
        Objects.requireNonNull(connection);
        synchronized (SESSION_LOCK) {
            ClientSession current = SESSION.get();
            if (current.connection() == connection) {
                return;
            }
            ACTIVE.set(LOCAL.get());
            SESSION.set(new ClientSession(
                    connection, SESSION_GENERATION.incrementAndGet()));
        }
    }

    public static void endSession(@Nullable Connection connection) {
        synchronized (SESSION_LOCK) {
            ClientSession current = SESSION.get();
            if (current.connection() == null
                    || connection != null
                            && current.connection() != connection
                    || connection == null
                            && Minecraft.getInstance().getConnection()
                                    != null) {
                return;
            }
            SESSION.set(new ClientSession(null, current.generation()));
            ACTIVE.set(LOCAL.get());
        }
    }

    public static boolean applyServer(
            SexPistolsConfigSyncPayload payload,
            Connection sourceConnection) {
        Objects.requireNonNull(payload);
        Objects.requireNonNull(sourceConnection);
        CALLBACKS_RECEIVED.incrementAndGet();
        synchronized (SESSION_LOCK) {
            ClientSession session = SESSION.get();
            ClientPacketListener listener =
                    Minecraft.getInstance().getConnection();
            if (session.connection() != sourceConnection
                    || listener == null
                    || listener.getConnection() != sourceConnection) {
                CALLBACKS_REJECTED_STALE_SESSION.incrementAndGet();
                return false;
            }
            if (!sourceConnection.isConnected()) {
                CALLBACKS_REJECTED_DISCONNECTED.incrementAndGet();
                return false;
            }
            ACTIVE.set(new Snapshot(
                    payload.bulletDamageMultiplier(),
                    payload.bulletDestroysBlocks(),
                    payload.blockDamageMemoryTicks(),
                    payload.stableDamageEndDistance(),
                    payload.closeDamageDropDistance(),
                    payload.minDistanceDamage(),
                    payload.maxRicochetCount(),
                    payload.targetSearchRange(),
                    payload.transferAssistRange(),
                    payload.reviveTicks(),
                    payload.hungerEnabled(),
                    payload.hungerMinTicks(),
                    payload.hungerExtraTicks(),
                    true));
            CALLBACKS_APPLIED.incrementAndGet();
            return true;
        }
    }

    public static void refreshLocal() {
        Snapshot local = localOrDefaults();
        synchronized (SESSION_LOCK) {
            LOCAL.set(local);
            ACTIVE.updateAndGet(current ->
                    current.serverAuthoritative() ? current : local);
        }
    }

    public static void resetToLocal() {
        synchronized (SESSION_LOCK) {
            ACTIVE.set(LOCAL.get());
        }
    }

    public static Diagnostics diagnostics() {
        ClientSession session = SESSION.get();
        return new Diagnostics(
                session.generation(),
                session.connection() != null,
                CALLBACKS_RECEIVED.get(),
                CALLBACKS_APPLIED.get(),
                CALLBACKS_REJECTED_DISCONNECTED.get(),
                CALLBACKS_REJECTED_STALE_SESSION.get());
    }

    public static void clearLocal() {
        Snapshot defaults = defaults();
        synchronized (SESSION_LOCK) {
            LOCAL.set(defaults);
            ACTIVE.updateAndGet(current ->
                    current.serverAuthoritative()
                            ? current
                            : defaults);
        }
    }

    private static Snapshot localOrDefaults() {
        if (!SexPistolsConfig.SPEC.isLoaded()) {
            return defaults();
        }
        return new Snapshot(
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
                SexPistolsConfig.hungerExtraTicks(),
                false);
    }

    private static Snapshot defaults() {
        return new Snapshot(
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
                SexPistolsConfig.DEFAULT_SEX_PISTOLS_HUNGER_EXTRA_TICKS,
                false);
    }

    public record Snapshot(
            double bulletDamageMultiplier,
            boolean bulletDestroysBlocks,
            int blockDamageMemoryTicks,
            double stableDamageEndDistance,
            double closeDamageDropDistance,
            double minDistanceDamage,
            int maxRicochetCount,
            double targetSearchRange,
            double transferAssistRange,
            int reviveTicks,
            boolean hungerEnabled,
            int hungerMinTicks,
            int hungerExtraTicks,
            boolean serverAuthoritative) {}

    public record Diagnostics(
            long sessionGeneration,
            boolean sessionActive,
            long callbacksReceived,
            long callbacksApplied,
            long callbacksRejectedDisconnected,
            long callbacksRejectedStaleSession) {}

    private record ClientSession(
            Connection connection,
            long generation) {
        private static ClientSession disconnected() {
            return new ClientSession(null, 0L);
        }
    }
}

package rotp.addons.kraftwork.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.addons.kraftwork.KraftWorkAddon;
import rotp.addons.kraftwork.state.KraftWorkLockService;
import rotp.addons.kraftwork.state.KraftWorkLockService.ReleaseFilter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class KraftWorkPayloads {
    public static final String NETWORK_PROTOCOL_VERSION = "2";
    public static final int SESSION_LIFETIME_TICKS = 200;
    private static final Map<UUID, PendingSession> PENDING =
            new HashMap<>();
    private static final Set<UUID> STAND_HUD_HIDDEN =
            new HashSet<>();
    private static Consumer<OpenAdvancedReleasePayload>
            clientOpenHandler = payload -> {};

    private KraftWorkPayloads() {}

    public static ResourceLocation id(String path) {
        return KraftWorkAddon.id(path);
    }

    public static void register(
            RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event
                .registrar(NETWORK_PROTOCOL_VERSION)
                .executesOn(HandlerThread.MAIN);
        registrar.playToClient(
                OpenAdvancedReleasePayload.TYPE,
                OpenAdvancedReleasePayload.STREAM_CODEC,
                KraftWorkPayloads::handleOpen);
        registrar.playToServer(
                SelectReleasePayload.TYPE,
                SelectReleasePayload.STREAM_CODEC,
                KraftWorkPayloads::handleSelection);
        registrar.playToServer(
                StandHudPayload.TYPE,
                StandHudPayload.STREAM_CODEC,
                KraftWorkPayloads::handleStandHud);
    }

    public static void installClientOpenHandler(
            Consumer<OpenAdvancedReleasePayload> handler) {
        clientOpenHandler = Objects.requireNonNull(handler);
    }

    public static void open(ServerPlayer player) {
        if (!player.isAlive()
                || player.isSpectator()
                || !KraftWorkAddon.isKraftWorkUser(player)) {
            clear(player);
            return;
        }
        UUID sessionId = UUID.randomUUID();
        long expiry = player.serverLevel().getGameTime()
                + SESSION_LIFETIME_TICKS;
        PENDING.put(
                player.getUUID(),
                new PendingSession(
                        sessionId,
                        player.level().dimension(),
                        expiry));
        PacketDistributor.sendToPlayer(
                player,
                new OpenAdvancedReleasePayload(sessionId));
    }

    public static void clear(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    public static void clearAll() {
        PENDING.clear();
        STAND_HUD_HIDDEN.clear();
    }

    private static void handleOpen(
            OpenAdvancedReleasePayload payload,
            IPayloadContext context) {
        clientOpenHandler.accept(payload);
    }

    private static void handleSelection(
            SelectReleasePayload payload,
            IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        PendingSession session = PENDING.get(player.getUUID());
        if (session == null
                || !session.id().equals(payload.sessionId())) {
            return;
        }
        PENDING.remove(player.getUUID());
        ReleaseFilter filter = payload.filter();
        ServerLevel level = player.serverLevel();
        if (filter == null
                || !player.isAlive()
                || player.isSpectator()
                || level.getGameTime()
                        > session.expiryGameTick()
                || !level.dimension().equals(session.dimension())
                || !KraftWorkAddon.isKraftWorkUser(player)) {
            return;
        }
        // each released projectile plays 1.16's sound at itself
        KraftWorkLockService.releaseProjectiles(player, filter);
    }

    private static void handleStandHud(
            StandHudPayload payload,
            IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            recordStandHud(player, payload.shown());
        }
    }

    // a player whose client never told otherwise has the Stand on its HUD
    public static boolean standHudShown(Player player) {
        return !STAND_HUD_HIDDEN.contains(player.getUUID());
    }

    public static void recordStandHud(ServerPlayer player, boolean shown) {
        if (shown) {
            STAND_HUD_HIDDEN.remove(player.getUUID());
        }
        else {
            STAND_HUD_HIDDEN.add(player.getUUID());
        }
    }

    public static void forgetStandHud(ServerPlayer player) {
        STAND_HUD_HIDDEN.remove(player.getUUID());
    }

    private record PendingSession(
            UUID id,
            ResourceKey<Level> dimension,
            long expiryGameTick) {}
}

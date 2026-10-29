package rotp.addons.weatherreport.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.weatherreport.WeatherReportAddon;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class WeatherSelectionSessions {
    public static final int SESSION_LIFETIME_TICKS = 200;
    private static final byte ALLOWED_CHOICES =
            (byte) WeatherChoice.ALL_ALLOWED_MASK;
    private static final Map<UUID, PendingSession> PENDING =
            new HashMap<>();
    private static final Map<UUID, OpenAttemptReceipt> LAST_OPEN_ATTEMPT =
            new HashMap<>();
    private static final Map<UUID, RemovalReceipt> LAST_REMOVAL =
            new HashMap<>();

    private WeatherSelectionSessions() {}

    public static SelectionSession open(ServerPlayer player) {
        String validationFailure = validationFailure(player);
        long gameTime = player.serverLevel().getGameTime();
        if (validationFailure != null) {
            LAST_OPEN_ATTEMPT.put(
                    player.getUUID(),
                    new OpenAttemptReceipt(
                            gameTime, false, validationFailure));
            return null;
        }

        UUID sessionId = UUID.randomUUID();
        long expiry = gameTime + SESSION_LIFETIME_TICKS;
        PendingSession session = new PendingSession(
                sessionId,
                player.level().dimension(),
                expiry,
                ALLOWED_CHOICES);
        PendingSession replaced = PENDING.put(player.getUUID(), session);
        if (replaced != null) {
            LAST_REMOVAL.put(
                    player.getUUID(),
                    new RemovalReceipt(
                            gameTime,
                            replaced.id(),
                            "overwritten_by_open",
                            sessionId,
                            (byte) -1,
                            false));
        }
        LAST_OPEN_ATTEMPT.put(
                player.getUUID(),
                new OpenAttemptReceipt(gameTime, true, "opened"));
        PacketDistributor.sendToPlayer(
                player,
                new OpenWeatherSelectionPayload(
                        sessionId,
                        expiry,
                        ALLOWED_CHOICES));
        return new SelectionSession(
                sessionId, expiry, ALLOWED_CHOICES);
    }

    public static boolean consumeAndApply(
            ServerPlayer player,
            UUID sessionId,
            byte choiceId) {
        PendingSession session = PENDING.get(player.getUUID());
        if (session == null || !session.id().equals(sessionId)) {
            return false;
        }

        PENDING.remove(player.getUUID());
        WeatherChoice choice = WeatherChoice.fromNetworkId(choiceId)
                .orElse(null);
        ServerLevel level = player.serverLevel();
        String rejection = selectionRejection(
                player, level, session, choice);
        if (rejection != null) {
            LAST_REMOVAL.put(
                    player.getUUID(),
                    new RemovalReceipt(
                            level.getGameTime(),
                            session.id(),
                            "consume_rejected_" + rejection,
                            sessionId,
                            choiceId,
                            false));
            return false;
        }

        choice.apply(level);
        LAST_REMOVAL.put(
                player.getUUID(),
                new RemovalReceipt(
                        level.getGameTime(),
                        session.id(),
                        "consume_applied",
                        sessionId,
                        choiceId,
                        true));
        return true;
    }

    public static void clear(ServerPlayer player) {
        PendingSession removed = PENDING.remove(player.getUUID());
        if (removed != null) {
            LAST_REMOVAL.put(
                    player.getUUID(),
                    new RemovalReceipt(
                            player.serverLevel().getGameTime(),
                            removed.id(),
                            "clear_player",
                            null,
                            (byte) -1,
                            false));
        }
        else {
            LAST_REMOVAL.remove(player.getUUID());
        }
        LAST_OPEN_ATTEMPT.remove(player.getUUID());
    }

    public static void clearAll() {
        clearAll(-1L);
    }

    public static void clearAll(long gameTick) {
        LAST_REMOVAL.clear();
        for (Map.Entry<UUID, PendingSession> entry : PENDING.entrySet()) {
            LAST_REMOVAL.put(
                    entry.getKey(),
                    new RemovalReceipt(
                            gameTick,
                            entry.getValue().id(),
                            "clear_all",
                            null,
                            (byte) -1,
                            false));
        }
        PENDING.clear();
        LAST_OPEN_ATTEMPT.clear();
    }

    public static int pendingCount() {
        return PENDING.size();
    }

    public static Optional<SelectionSession> currentSession(
            ServerPlayer player) {
        PendingSession session = PENDING.get(player.getUUID());
        return session != null
                ? Optional.of(new SelectionSession(
                        session.id(),
                        session.expiryGameTick(),
                        session.allowedChoices()))
                : Optional.empty();
    }

    public static Optional<OpenAttemptReceipt> lastOpenAttempt(
            ServerPlayer player) {
        return Optional.ofNullable(
                LAST_OPEN_ATTEMPT.get(player.getUUID()));
    }

    public static Optional<RemovalReceipt> lastRemoval(
            ServerPlayer player) {
        return Optional.ofNullable(
                LAST_REMOVAL.get(player.getUUID()));
    }

    private static String selectionRejection(
            ServerPlayer player,
            ServerLevel level,
            PendingSession session,
            WeatherChoice choice) {
        if (choice == null) {
            return "invalid_choice";
        }
        if (!player.isAlive()) {
            return "player_dead";
        }
        if (player.isSpectator()) {
            return "player_spectator";
        }
        if (level.getGameTime() > session.expiryGameTick()) {
            return "session_expired";
        }
        if (!player.level().dimension().equals(session.dimension())) {
            return "dimension_changed";
        }
        if ((session.allowedChoices() & choice.mask()) == 0) {
            return "choice_not_allowed";
        }
        return validationFailure(player);
    }

    private static String validationFailure(ServerPlayer player) {
        StandPower power = StandPower.get(player);
        if (power == null) {
            return "stand_power_missing";
        }
        if (!power.hasPower()) {
            return "stand_power_absent";
        }
        if (power.getPowerType()
                != WeatherReportAddon.WEATHER_REPORT.get()) {
            return "wrong_stand_type";
        }
        var typeData = power.getCurTypeData();
        if (power.isUserCreative()) {
            return null;
        }
        if (typeData == null) {
            return "stand_type_data_missing";
        }
        return typeData.isSkillUnlocked(
                WeatherReportAddon.CHANGE_WEATHER_NAME)
                ? null
                : "change_weather_skill_locked";
    }

    private record PendingSession(
            UUID id,
            ResourceKey<Level> dimension,
            long expiryGameTick,
            byte allowedChoices) {}

    public record SelectionSession(
            UUID id,
            long expiryGameTick,
            byte allowedChoices) {}

    public record OpenAttemptReceipt(
            long gameTick,
            boolean opened,
            String result) {}

    public record RemovalReceipt(
            long gameTick,
            UUID removedSessionId,
            String reason,
            UUID relatedSessionId,
            byte choiceId,
            boolean authenticatedApply) {}
}

package rotp.core.impl.npc.rps;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class RPSPvpGamesMap {
    private static final String PAUSED_KEY = "PausedPvpGames";

    private final Map<UUID, RockPaperScissorsGame> activeGames = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    // unfinished PvP matches ended by a quit, kept per player pair (1.16 resumes them)
    private final Map<PlayerPair, Map<UUID, RockPaperScissorsGame>> pausedGames = new HashMap<>();

    public void put(ServerPlayer player, UUID opponent, boolean opponentIsNpc) {
        clearInvites(player.getUUID());
        activeGames.put(player.getUUID(), new RockPaperScissorsGame(player, opponent, opponentIsNpc));
    }

    public void invite(ServerPlayer sender, ServerPlayer target) {
        pendingInvites.put(target.getUUID(), sender.getUUID());
    }

    public boolean consumeInvite(ServerPlayer sender, ServerPlayer target) {
        UUID inviter = pendingInvites.get(sender.getUUID());
        if (target.getUUID().equals(inviter)) {
            clearInvites(sender.getUUID());
            clearInvites(target.getUUID());
            return true;
        }
        return false;
    }

    public boolean has(UUID player) {
        return activeGames.containsKey(player);
    }

    public RockPaperScissorsGame get(UUID player) {
        return activeGames.get(player);
    }

    public void remove(UUID player) {
        activeGames.remove(player);
        clearInvites(player);
    }

    /** True when both current games point at each other as one PvP match. */
    public boolean isMutualPvp(UUID player, UUID opponent) {
        RockPaperScissorsGame game = activeGames.get(player);
        RockPaperScissorsGame opponentGame = activeGames.get(opponent);
        return game != null && opponentGame != null
                && !game.opponentIsNpc() && !opponentGame.opponentIsNpc()
                && opponent.equals(game.opponent())
                && player.equals(opponentGame.opponent());
    }

    /**
     * Ends the player's current game. A mutual PvP match ends for the opponent too
     * and, if unfinished, is paused for the pair. A stale opponent game is left alone.
     *
     * @return the PvP opponent whose game was ended, or null
     */
    @Nullable
    public UUID leave(UUID player) {
        RockPaperScissorsGame game = activeGames.get(player);
        if (game == null) {
            return null;
        }
        UUID opponent = game.opponent();
        boolean mutual = isMutualPvp(player, opponent);
        RockPaperScissorsGame opponentGame = activeGames.get(opponent);
        remove(player);
        if (!mutual) {
            return null;
        }
        remove(opponent);
        if (!game.isMatchOver()) {
            Map<UUID, RockPaperScissorsGame> pair = new HashMap<>();
            pair.put(player, game);
            pair.put(opponent, opponentGame);
            pausedGames.put(PlayerPair.of(player, opponent), pair);
        }
        return opponent;
    }

    /**
     * Starts a PvP match for an accepted invite, resuming the pair's paused match if any.
     *
     * @return true if a paused match was resumed
     */
    public boolean startPvp(ServerPlayer player, ServerPlayer target) {
        Map<UUID, RockPaperScissorsGame> paused =
                pausedGames.remove(PlayerPair.of(player.getUUID(), target.getUUID()));
        RockPaperScissorsGame playerGame = paused != null ? paused.get(player.getUUID()) : null;
        RockPaperScissorsGame targetGame = paused != null ? paused.get(target.getUUID()) : null;
        if (playerGame != null && targetGame != null) {
            clearInvites(player.getUUID());
            clearInvites(target.getUUID());
            activeGames.put(player.getUUID(), resumed(playerGame));
            activeGames.put(target.getUUID(), resumed(targetGame));
            return true;
        }
        put(player, target.getUUID(), false);
        put(target, player.getUUID(), false);
        return false;
    }

    // same score and pick history, no pending picks, fresh session epoch;
    // the round's cheat is usable again (1.16 resets it per screen)
    private static RockPaperScissorsGame resumed(RockPaperScissorsGame game) {
        CompoundTag tag = game.save();
        tag.remove("PlayerPick");
        tag.remove("OpponentPick");
        tag.remove("OpponentThoughtsPick");
        tag.remove("SessionEpoch");
        tag.remove("CheatUsedRound");
        return RockPaperScissorsGame.load(tag);
    }

    private void clearInvites(UUID player) {
        pendingInvites.remove(player);
        pendingInvites.entrySet().removeIf(entry -> entry.getValue().equals(player));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        int i = 0;
        for (var entry : activeGames.entrySet()) {
            tag.put("Game" + i++, entry.getValue().save());
        }
        CompoundTag pausedTag = new CompoundTag();
        int j = 0;
        for (Map<UUID, RockPaperScissorsGame> pair : pausedGames.values()) {
            for (RockPaperScissorsGame game : pair.values()) {
                pausedTag.put("Game" + j++, game.save());
            }
        }
        tag.put(PAUSED_KEY, pausedTag);
        return tag;
    }

    public void load(CompoundTag tag) {
        activeGames.clear();
        pausedGames.clear();
        for (String key : tag.getAllKeys()) {
            if (PAUSED_KEY.equals(key)) {
                continue;
            }
            CompoundTag gameTag = tag.getCompound(key);
            RockPaperScissorsGame game = RockPaperScissorsGame.load(gameTag);
            activeGames.put(game.player(), game);
        }
        CompoundTag pausedTag = tag.getCompound(PAUSED_KEY);
        for (String key : pausedTag.getAllKeys()) {
            RockPaperScissorsGame game = RockPaperScissorsGame.load(pausedTag.getCompound(key));
            pausedGames.computeIfAbsent(PlayerPair.of(game.player(), game.opponent()), pair -> new HashMap<>())
                    .put(game.player(), game);
        }
    }

    private record PlayerPair(UUID first, UUID second) {
        static PlayerPair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new PlayerPair(a, b) : new PlayerPair(b, a);
        }
    }
}

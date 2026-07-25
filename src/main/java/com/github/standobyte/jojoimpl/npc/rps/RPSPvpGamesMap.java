package com.github.standobyte.jojoimpl.npc.rps;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public class RPSPvpGamesMap {
    private final Map<UUID, RockPaperScissorsGame> activeGames = new HashMap<>();
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

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
        return tag;
    }

    public void load(CompoundTag tag) {
        activeGames.clear();
        for (String key : tag.getAllKeys()) {
            CompoundTag gameTag = tag.getCompound(key);
            RockPaperScissorsGame game = RockPaperScissorsGame.load(gameTag);
            activeGames.put(game.player(), game);
        }
    }
}

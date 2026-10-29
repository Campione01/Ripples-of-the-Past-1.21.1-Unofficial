package rotp.addons.spawn_with_stand.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerDataHelper extends SavedData {
    public static final String DATA_NAME =
            "rotp_sws_player_data";
    public static final String PLAYERS_TAG = "Players";
    public static final String JOIN_COUNTER_TAG =
            "JoinCounter";

    private static final SavedData.Factory<PlayerDataHelper>
            FACTORY = new SavedData.Factory<>(
                    PlayerDataHelper::new,
                    PlayerDataHelper::load);

    private final Set<UUID> playersWhoRolled =
            new HashSet<>();
    private int joinCounter;

    private PlayerDataHelper() {}

    public static PlayerDataHelper get(
            MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static PlayerDataHelper load(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        PlayerDataHelper data = new PlayerDataHelper();
        CompoundTag playersTag =
                tag.getCompound(PLAYERS_TAG);
        for (String uuid : playersTag.getAllKeys()) {
            try {
                data.playersWhoRolled.add(
                        UUID.fromString(uuid));
            }
            catch (IllegalArgumentException ignored) {
                // Ignore corrupt legacy keys without blocking the world.
            }
        }
        data.joinCounter = Math.max(
                0, tag.getInt(JOIN_COUNTER_TAG));
        return data;
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries) {
        CompoundTag playersTag = new CompoundTag();
        playersWhoRolled.stream()
                .map(UUID::toString)
                .sorted()
                .forEach(uuid ->
                        playersTag.putBoolean(uuid, true));
        tag.put(PLAYERS_TAG, playersTag);
        tag.putInt(JOIN_COUNTER_TAG, joinCounter);
        return tag;
    }

    public boolean hasRolledStand(UUID playerId) {
        return playersWhoRolled.contains(playerId);
    }

    public void setHasRolledStand(UUID playerId) {
        if (playersWhoRolled.add(playerId)) {
            setDirty();
        }
    }

    public int getAndIncrementJoinOrder() {
        int current = joinCounter;
        if (joinCounter < Integer.MAX_VALUE) {
            joinCounter++;
        }
        setDirty();
        return current;
    }

    public int joinCounter() {
        return joinCounter;
    }

    public int rolledPlayerCount() {
        return playersWhoRolled.size();
    }
}

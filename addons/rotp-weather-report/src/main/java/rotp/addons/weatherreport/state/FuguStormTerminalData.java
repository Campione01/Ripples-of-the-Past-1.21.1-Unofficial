package rotp.addons.weatherreport.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import rotp.addons.weatherreport.entity.WeatherReportFugaSummonerEntity;

public final class FuguStormTerminalData extends SavedData {
    private static final String DATA_NAME = "rotp_wr_fugu_terminals";
    private static final Factory<FuguStormTerminalData> FACTORY =
            new Factory<>(FuguStormTerminalData::new,
                    FuguStormTerminalData::load, null);

    private final Map<UUID, Long> terminals = new HashMap<>();
    private long lastPruneTick = Long.MIN_VALUE;

    private FuguStormTerminalData() {}

    public static FuguStormTerminalData get(ServerLevel level) {
        FuguStormTerminalData data = level.getDataStorage()
                .computeIfAbsent(FACTORY, DATA_NAME);
        data.prune(level.getGameTime());
        return data;
    }

    public static void pruneIfPresent(ServerLevel level) {
        FuguStormTerminalData data = level.getDataStorage().get(FACTORY, DATA_NAME);
        if (data != null) {
            data.prune(level.getGameTime());
        }
    }

    public void markTerminal(UUID uuid, long gameTime) {
        // Every previously issued child deadline is at most this far away.
        terminals.put(uuid, gameTime
                + WeatherReportFugaSummonerEntity.LIFETIME_TICKS);
        setDirty();
    }

    public boolean isTerminal(UUID uuid, long gameTime) {
        Long until = terminals.get(uuid);
        return until != null && gameTime < until;
    }

    private void prune(long gameTime) {
        if (lastPruneTick != gameTime) {
            lastPruneTick = gameTime;
            if (terminals.values().removeIf(until -> until <= gameTime)) {
                setDirty();
            }
        }
    }

    private static FuguStormTerminalData load(
            CompoundTag tag, HolderLookup.Provider registries) {
        FuguStormTerminalData data = new FuguStormTerminalData();
        ListTag list = tag.getList("Terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("UUID") && entry.contains("Until", Tag.TAG_LONG)) {
                data.terminals.put(entry.getUUID("UUID"), entry.getLong("Until"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        terminals.forEach((uuid, until) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", uuid);
            entry.putLong("Until", until);
            list.add(entry);
        });
        tag.put("Terminals", list);
        return tag;
    }
}

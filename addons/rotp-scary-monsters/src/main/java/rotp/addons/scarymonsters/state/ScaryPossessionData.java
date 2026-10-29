package rotp.addons.scarymonsters.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

public final class ScaryPossessionData extends SavedData {
    private static final String DATA_NAME = "scary_monsters_possessions";
    private static final Factory<ScaryPossessionData> FACTORY =
            new Factory<>(ScaryPossessionData::new, ScaryPossessionData::load, null);

    private final Map<UUID, Possession> possessions = new HashMap<>();

    private ScaryPossessionData() {}

    public static ScaryPossessionData get(ServerLevel level) {
        // One server-wide record remains available when the player changes dimension or is offline.
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Possession get(UUID player) {
        return possessions.get(player);
    }

    public boolean begin(UUID player, UUID dinosaur, GameType originalMode, boolean legacy) {
        if (possessions.containsKey(player)) return false;
        possessions.put(player, new Possession(dinosaur, originalMode, false, legacy, null, null));
        setDirty();
        return true;
    }

    /** The spot is where the dinosaur (or its camouflage stone) ended; an offline captive is put there on login. */
    public void markTerminal(UUID player, UUID dinosaur,
            @Nullable ResourceKey<Level> releaseDimension, @Nullable Vec3 releaseSpot) {
        Possession current = possessions.get(player);
        if (current != null && current.dinosaur().equals(dinosaur) && !current.terminal()) {
            boolean located = releaseDimension != null && releaseSpot != null;
            possessions.put(player, new Possession(dinosaur, current.originalMode(), true, current.legacy(),
                    located ? releaseDimension : null, located ? releaseSpot : null));
            setDirty();
        }
    }

    public boolean remove(UUID player, UUID dinosaur) {
        Possession current = possessions.get(player);
        if (current == null || !current.dinosaur().equals(dinosaur)) return false;
        possessions.remove(player);
        setDirty();
        return true;
    }

    public static ScaryPossessionData load(CompoundTag tag, HolderLookup.Provider registries) {
        ScaryPossessionData data = new ScaryPossessionData();
        ListTag entries = tag.getList("Possessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Player") || !entry.hasUUID("Dinosaur")
                    || !entry.contains("GameMode", Tag.TAG_INT)) continue;
            int mode = entry.getInt("GameMode");
            if (mode < 0 || mode > 3) continue;
            ResourceLocation dimension = entry.contains("ReleaseDimension", Tag.TAG_STRING)
                    ? ResourceLocation.tryParse(entry.getString("ReleaseDimension")) : null;
            boolean located = dimension != null && entry.contains("ReleaseX", Tag.TAG_DOUBLE)
                    && entry.contains("ReleaseY", Tag.TAG_DOUBLE) && entry.contains("ReleaseZ", Tag.TAG_DOUBLE);
            data.possessions.put(entry.getUUID("Player"), new Possession(entry.getUUID("Dinosaur"),
                    GameType.byId(mode), entry.getBoolean("Terminal"), entry.getBoolean("Legacy"),
                    located ? ResourceKey.create(Registries.DIMENSION, dimension) : null,
                    located ? new Vec3(entry.getDouble("ReleaseX"), entry.getDouble("ReleaseY"),
                            entry.getDouble("ReleaseZ")) : null));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        possessions.forEach((player, possession) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putUUID("Dinosaur", possession.dinosaur());
            entry.putInt("GameMode", possession.originalMode().getId());
            entry.putBoolean("Terminal", possession.terminal());
            entry.putBoolean("Legacy", possession.legacy());
            if (possession.releaseDimension() != null && possession.releaseSpot() != null) {
                entry.putString("ReleaseDimension", possession.releaseDimension().location().toString());
                entry.putDouble("ReleaseX", possession.releaseSpot().x);
                entry.putDouble("ReleaseY", possession.releaseSpot().y);
                entry.putDouble("ReleaseZ", possession.releaseSpot().z);
            }
            entries.add(entry);
        });
        tag.put("Possessions", entries);
        return tag;
    }

    public record Possession(UUID dinosaur, GameType originalMode, boolean terminal, boolean legacy,
            @Nullable ResourceKey<Level> releaseDimension, @Nullable Vec3 releaseSpot) {}
}

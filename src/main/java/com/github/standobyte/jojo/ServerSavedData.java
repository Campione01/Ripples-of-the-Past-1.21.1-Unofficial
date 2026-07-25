package com.github.standobyte.jojo;

import java.util.UUID;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.subsystems.itemtracking.ItemTracking;
import com.github.standobyte.jojo.util.functions.NBTUtil;
import com.github.standobyte.jojoimpl.npc.rps.RPSPvpGamesMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

public class ServerSavedData extends SavedData {
    public ItemTracking itemsTracker = new ItemTracking(this);
    public final RPSPvpGamesMap rpsPvpGames = new RPSPvpGamesMap();
    public boolean foundFirstArrows = false;
    public boolean foundBeetleArrow = false;
    private int walkmanId = 0;
    private int cassetteId = 0;
    private long polaroidPhotoId = 0;
    private UUID serverUUID = UUID.randomUUID();

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag nbt = new CompoundTag();
		nbt.put("ItemTrackers", itemsTracker.serializeNBT(registries));
		nbt.put("RPSPvpGames", rpsPvpGames.save());
		nbt.putBoolean("foundFirstArrows", foundFirstArrows);
		nbt.putBoolean("foundBeetleArrow", foundBeetleArrow);
		nbt.putInt("WalkmanId", walkmanId);
		nbt.putInt("CassetteId", cassetteId);
		nbt.putLong("PolaroidPhotoId", polaroidPhotoId);
		nbt.putUUID("ServerUUID", serverUUID);
		return nbt;
	}
	
    public static ServerSavedData load(CompoundTag nbt, HolderLookup.Provider registries) {
    	ServerSavedData data = new ServerSavedData();
    	NBTUtil.getElementOptional(nbt, "ItemTrackers", ListTag.class).ifPresent(
    			trackersNbt -> data.itemsTracker.deserializeNBT(registries, trackersNbt));
    	if (nbt.contains("RPSPvpGames", 10)) {
    		data.rpsPvpGames.load(nbt.getCompound("RPSPvpGames"));
    	}
    	data.foundFirstArrows = nbt.getBoolean("foundFirstArrows");
    	data.foundBeetleArrow = nbt.getBoolean("foundBeetleArrow");
    	data.walkmanId = nbt.getInt("WalkmanId");
    	data.cassetteId = nbt.getInt("CassetteId");
    	data.polaroidPhotoId = nbt.getLong("PolaroidPhotoId");
    	if (nbt.hasUUID("ServerUUID")) {
    		data.serverUUID = nbt.getUUID("ServerUUID");
    	}
        return data;
    }

    public int incWalkmanId() {
    	walkmanId++;
    	setDirty();
    	return walkmanId;
    }

    public int incCassetteId() {
    	cassetteId++;
    	setDirty();
    	return cassetteId;
    }

    public long incPolaroidPhotoId() {
    	polaroidPhotoId++;
    	setDirty();
    	return polaroidPhotoId;
    }

    public UUID getServerUUID() {
    	setDirty();
    	return serverUUID;
    }
    
	
    protected static final String fileName = JojoMod.MOD_ID + "-server_data";
	public static ServerSavedData get(MinecraftServer server) {
		DimensionDataStorage storage = server.overworld().getDataStorage();
		return storage.computeIfAbsent(new SavedData.Factory<>(
				ServerSavedData::new, ServerSavedData::load), fileName);
	}

}

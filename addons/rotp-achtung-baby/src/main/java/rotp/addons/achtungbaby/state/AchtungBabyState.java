package rotp.addons.achtungbaby.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class AchtungBabyState
        implements INBTSerializable<CompoundTag> {
    private static final String PERFECT = "PerfectInvisible";
    private static final String GHOST = "GhostMode";
    private static final String ORIGINAL_GAME_MODE = "OriginalGameMode";
    private static final String GHOST_START = "GhostStartTick";
    private static final String GHOST_PENALTY_UNTIL = "GhostPenaltyUntil";

    private boolean perfectInvisible;
    private boolean ghostMode;
    private int originalGameMode = -1;
    private long ghostStartTick;
    private long ghostPenaltyUntil;

    public boolean perfectInvisible() {
        return perfectInvisible;
    }

    public boolean ghostMode() {
        return ghostMode;
    }

    public int originalGameMode() {
        return originalGameMode;
    }

    /** Game time at which the current ghost mode began, for the upkeep tick and the duration cap. */
    public long ghostStartTick() {
        return ghostStartTick;
    }

    /** Game time before which ghost mode refuses to start again, after stamina ran out under it. */
    public long ghostPenaltyUntil() {
        return ghostPenaltyUntil;
    }

    public void setGhostPenaltyUntil(long gameTime) {
        ghostPenaltyUntil = gameTime;
    }

    public void setPerfectInvisible(boolean value) {
        perfectInvisible = value;
    }

    public void beginGhostMode(int gameMode, long gameTime) {
        ghostMode = true;
        ghostStartTick = gameTime;
        if (originalGameMode < 0) {
            originalGameMode = gameMode;
        }
    }

    public void endGhostMode() {
        ghostMode = false;
        ghostStartTick = 0L;
        originalGameMode = -1;
    }

    public boolean clear() {
        boolean changed = perfectInvisible || ghostMode
                || originalGameMode >= 0;
        perfectInvisible = false;
        ghostMode = false;
        ghostStartTick = 0L;
        originalGameMode = -1;
        return changed;
    }

    public void applyClientSnapshot(
            boolean perfect, boolean ghost) {
        perfectInvisible = perfect;
        ghostMode = ghost;
        originalGameMode = -1;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PERFECT, perfectInvisible);
        tag.putBoolean(GHOST, ghostMode);
        tag.putInt(ORIGINAL_GAME_MODE, originalGameMode);
        tag.putLong(GHOST_START, ghostStartTick);
        tag.putLong(GHOST_PENALTY_UNTIL, ghostPenaltyUntil);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        perfectInvisible = tag.getBoolean(PERFECT);
        ghostMode = tag.getBoolean(GHOST);
        originalGameMode = tag.contains(ORIGINAL_GAME_MODE)
                ? tag.getInt(ORIGINAL_GAME_MODE)
                : -1;
        ghostStartTick = tag.getLong(GHOST_START);
        ghostPenaltyUntil = tag.getLong(GHOST_PENALTY_UNTIL);
    }
}

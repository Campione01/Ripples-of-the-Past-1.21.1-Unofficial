package rotp.addons.kraftwork.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class KraftWorkUserState
        implements INBTSerializable<CompoundTag> {
    private static final String SELF_LOCKED = "SelfLocked";
    private static final String BLOCKING_ITEMS = "BlockingItems";

    private boolean selfLocked;
    private boolean blockingItems;
    private float lastStamina = Float.NaN;
    private long energyPressTime = Long.MIN_VALUE;

    public boolean selfLocked() {
        return selfLocked;
    }

    public void setSelfLocked(boolean selfLocked) {
        this.selfLocked = selfLocked;
    }

    public boolean blockingItems() {
        return blockingItems;
    }

    public void setBlockingItems(boolean blockingItems) {
        this.blockingItems = blockingItems;
    }

    public float lastStamina() {
        return lastStamina;
    }

    public void setLastStamina(float lastStamina) {
        this.lastStamina = lastStamina;
    }

    // the game time of the last fresh energy accumulation press, for its
    // replay from the input buffer
    public long energyPressTime() {
        return energyPressTime;
    }

    public void setEnergyPressTime(long energyPressTime) {
        this.energyPressTime = energyPressTime;
    }

    public void clearRuntime() {
        selfLocked = false;
        blockingItems = false;
        lastStamina = Float.NaN;
        energyPressTime = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(SELF_LOCKED, selfLocked);
        tag.putBoolean(BLOCKING_ITEMS, blockingItems);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        selfLocked = tag.getBoolean(SELF_LOCKED);
        blockingItems = tag.getBoolean(BLOCKING_ITEMS);
        lastStamina = Float.NaN;
    }
}

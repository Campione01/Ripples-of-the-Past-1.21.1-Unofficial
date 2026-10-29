package rotp.addons.emperor.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class EmperorModeState
        implements INBTSerializable<CompoundTag> {
    private EmperorMode mode = EmperorMode.NONE;

    public EmperorMode mode() {
        return mode;
    }

    public boolean setMode(EmperorMode mode) {
        EmperorMode normalized =
                mode != null ? mode : EmperorMode.NONE;
        if (this.mode == normalized) {
            return false;
        }
        this.mode = normalized;
        return true;
    }

    public void copyFrom(EmperorModeState other) {
        setMode(other != null ? other.mode : EmperorMode.NONE);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", mode.ordinal());
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        mode = EmperorMode.byOrdinal(tag.getInt("Mode"));
    }
}

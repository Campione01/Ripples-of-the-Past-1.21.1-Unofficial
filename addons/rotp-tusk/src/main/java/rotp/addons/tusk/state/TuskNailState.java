package rotp.addons.tusk.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class TuskNailState
        extends TuskNailCounter
        implements INBTSerializable<CompoundTag> {
    private boolean infiniteRotationCharged;

    public boolean isInfiniteRotationCharged() {
        return infiniteRotationCharged;
    }

    public boolean setInfiniteRotationCharged(boolean charged) {
        if (infiniteRotationCharged == charged) {
            return false;
        }
        infiniteRotationCharged = charged;
        return true;
    }

    public boolean completeInfiniteRotation() {
        boolean changed = infiniteRotationCharged
                || nails() != MAX_NAILS
                || regenTicksRemaining() != 0;
        infiniteRotationCharged = false;
        applySnapshot(MAX_NAILS, 0);
        return changed;
    }

    @Override
    public boolean reset() {
        boolean changed = super.reset();
        return setInfiniteRotationCharged(false) || changed;
    }

    public void copyFrom(TuskNailState other) {
        super.copyFrom(other);
        infiniteRotationCharged = other.infiniteRotationCharged;
    }

    public void applySnapshot(
            int nails,
            int regenTicksRemaining,
            boolean charged) {
        super.applySnapshot(nails, regenTicksRemaining);
        infiniteRotationCharged = charged;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Nails", nails());
        tag.putInt("RegenTicksRemaining", regenTicksRemaining());
        tag.putBoolean(
                "InfiniteRotationCharged",
                infiniteRotationCharged);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        applySnapshot(
                tag.contains("Nails") ? tag.getInt("Nails") : MAX_NAILS,
                tag.getInt("RegenTicksRemaining"),
                tag.getBoolean("InfiniteRotationCharged"));
    }

}

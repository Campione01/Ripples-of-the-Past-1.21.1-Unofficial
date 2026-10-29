package rotp.addons.reworked.state;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ReworkedLivingState
        implements INBTSerializable<CompoundTag> {
    private static final String HEAT = "Heat";

    private float heat;

    public float heat() {
        return heat;
    }

    public void setHeat(float heat) {
        this.heat = Float.isFinite(heat)
                ? Math.max(0.0F, heat)
                : 0.0F;
    }

    public void addHeat(float additionalHeat) {
        setHeat(heat + additionalHeat);
    }

    public void addHeatRegressive(
            float heatAdditional, int multiplier) {
        addHeat(
                heatAdditional
                        / (heatAdditional + heat)
                        / multiplier);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(HEAT, heat);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        setHeat(tag.getFloat(HEAT));
    }
}

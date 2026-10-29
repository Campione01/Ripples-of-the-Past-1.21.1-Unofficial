package rotp.addons.rhcp.state;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class RhcpState implements INBTSerializable<CompoundTag> {
    public static final float MAX_ENERGY = 8000.0F;

    private float energy;
    private int potency;
    private boolean inConductor;
    private long conductorPos;
    private boolean blocking;
    private boolean dodging;
    private boolean boosting;
    private int targetId = -1;
    private int carriedTargetId = -1;
    private boolean carriedTargetWasNoPhysics;

    public float energy() {
        return energy;
    }

    public int potency() {
        return potency;
    }

    public boolean powered() {
        return potency > 0 && energy > 99.0F;
    }

    public boolean inConductor() {
        return inConductor;
    }

    public BlockPos conductorPos() {
        return BlockPos.of(conductorPos);
    }

    public boolean blocking() {
        return blocking;
    }

    public boolean dodging() {
        return dodging;
    }

    public boolean boosting() {
        return boosting;
    }

    public int targetId() {
        return targetId;
    }

    public int carriedTargetId() {
        return carriedTargetId;
    }

    public boolean carriedTargetWasNoPhysics() {
        return carriedTargetWasNoPhysics;
    }

    public boolean setEnergy(float value) {
        float clamped = Math.max(0.0F, Math.min(MAX_ENERGY, value));
        int nextPotency = potency;
        if (clamped <= 99.0F) {
            nextPotency = 0;
        }
        else if (clamped >= 400.0F && nextPotency == 0) {
            nextPotency = 1;
        }
        boolean changed = energy != clamped || potency != nextPotency;
        energy = clamped;
        potency = nextPotency;
        return changed;
    }

    public boolean addEnergy(float amount) {
        return setEnergy(energy + amount);
    }

    public boolean setPotency(int value) {
        int clamped = Math.max(0, Math.min(5, value));
        if (energy <= 99.0F) {
            clamped = 0;
        }
        boolean changed = potency != clamped;
        potency = clamped;
        return changed;
    }

    public boolean enterConductor(BlockPos pos) {
        long packed = pos.asLong();
        boolean changed = !inConductor || conductorPos != packed;
        inConductor = true;
        conductorPos = packed;
        return changed;
    }

    public boolean leaveConductor() {
        boolean changed = inConductor || carriedTargetId >= 0;
        inConductor = false;
        conductorPos = 0L;
        targetId = -1;
        clearCarriedTarget();
        return changed;
    }

    public void setBlocking(boolean value) {
        blocking = value;
    }

    public void setDodging(boolean value) {
        dodging = value;
    }

    public void setBoosting(boolean value) {
        boosting = value;
    }

    public void setTargetId(int value) {
        targetId = value;
    }

    public void setCarriedTarget(int entityId, boolean wasNoPhysics) {
        carriedTargetId = entityId;
        carriedTargetWasNoPhysics = wasNoPhysics;
    }

    public boolean clearCarriedTarget() {
        boolean changed = carriedTargetId >= 0;
        carriedTargetId = -1;
        carriedTargetWasNoPhysics = false;
        return changed;
    }

    public boolean clearTransient() {
        boolean changed = inConductor || blocking || dodging
                || boosting || targetId >= 0 || carriedTargetId >= 0;
        inConductor = false;
        conductorPos = 0L;
        blocking = false;
        dodging = false;
        boosting = false;
        targetId = -1;
        clearCarriedTarget();
        return changed;
    }

    public boolean clearAll() {
        boolean transientChanged = clearTransient();
        boolean changed = energy != 0.0F || potency != 0
                || transientChanged;
        energy = 0.0F;
        potency = 0;
        return changed;
    }

    public void applyClientSnapshot(
            float nextEnergy,
            int nextPotency,
            boolean nextInConductor,
            long nextConductorPos,
            boolean nextBlocking,
            boolean nextDodging,
            boolean nextBoosting,
            int nextTargetId) {
        energy = nextEnergy;
        potency = nextPotency;
        inConductor = nextInConductor;
        conductorPos = nextConductorPos;
        blocking = nextBlocking;
        dodging = nextDodging;
        boosting = nextBoosting;
        targetId = nextTargetId;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Energy", energy);
        tag.putInt("Potency", potency);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        energy = Math.max(
                0.0F,
                Math.min(MAX_ENERGY, tag.getFloat("Energy")));
        potency = Math.max(
                0,
                Math.min(5, tag.getInt("Potency")));
        if (energy <= 99.0F) {
            potency = 0;
        }
        clearTransient();
    }
}

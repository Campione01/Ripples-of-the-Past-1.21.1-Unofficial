package rotp.addons.speedking.state;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class SpeedKingState
        implements INBTSerializable<CompoundTag> {
    private boolean heatTrap;
    private long heatTrapPos;
    private String heatTrapDimension = "";
    private int grabbedTargetId = -1;
    private boolean blocking;
    private UUID heatVisionLeaseId;
    private long heatVisionExpiresAt;
    private String heatVisionDimension = "";

    public boolean hasHeatTrap() {
        return heatTrap;
    }

    public BlockPos heatTrapPos() {
        return BlockPos.of(heatTrapPos);
    }

    public String heatTrapDimension() {
        return heatTrapDimension;
    }

    public void setHeatTrap(BlockPos pos, String dimension) {
        heatTrap = true;
        heatTrapPos = pos.asLong();
        heatTrapDimension = dimension;
    }

    public boolean clearHeatTrap() {
        boolean changed = heatTrap;
        heatTrap = false;
        heatTrapPos = 0L;
        heatTrapDimension = "";
        return changed;
    }

    public int grabbedTargetId() {
        return grabbedTargetId;
    }

    public void setGrabbedTargetId(int value) {
        grabbedTargetId = value;
    }

    public boolean blocking() {
        return blocking;
    }

    public void setBlocking(boolean value) {
        blocking = value;
    }

    public long heatVisionExpiresAt() {
        return heatVisionExpiresAt;
    }

    public UUID heatVisionLeaseId() {
        return heatVisionLeaseId;
    }

    public String heatVisionDimension() {
        return heatVisionDimension;
    }

    public void beginHeatVisionLease(
            UUID leaseId, long expiresAt, String dimension) {
        heatVisionLeaseId = leaseId;
        heatVisionExpiresAt = expiresAt;
        heatVisionDimension = dimension;
    }

    public boolean matchesHeatVisionLease(
            UUID leaseId, long expiresAt, String dimension) {
        return heatVisionLeaseId != null
                && heatVisionLeaseId.equals(leaseId)
                && heatVisionExpiresAt == expiresAt
                && heatVisionDimension.equals(dimension);
    }

    public boolean clearHeatVisionLease() {
        boolean changed = heatVisionLeaseId != null
                || heatVisionExpiresAt > 0L
                || !heatVisionDimension.isEmpty();
        heatVisionLeaseId = null;
        heatVisionExpiresAt = 0L;
        heatVisionDimension = "";
        return changed;
    }

    public boolean clearTransient() {
        boolean changed = grabbedTargetId >= 0
                || blocking
                || heatVisionLeaseId != null
                || heatVisionExpiresAt > 0L;
        grabbedTargetId = -1;
        blocking = false;
        clearHeatVisionLease();
        return changed;
    }

    public boolean clearAll() {
        boolean transientChanged = clearTransient();
        boolean changed = heatTrap || transientChanged;
        clearHeatTrap();
        return changed;
    }

    public void applyClientSnapshot(
            boolean hasTrap, long trapPos, String dimension) {
        heatTrap = hasTrap;
        heatTrapPos = trapPos;
        heatTrapDimension = dimension;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (heatTrap) {
            tag.putBoolean("HeatTrap", true);
            tag.putLong("HeatTrapPos", heatTrapPos);
            tag.putString("HeatTrapDimension", heatTrapDimension);
        }
        if (heatVisionLeaseId != null) {
            tag.putUUID("HeatVisionLease", heatVisionLeaseId);
            tag.putLong("HeatVisionExpiresAt", heatVisionExpiresAt);
            tag.putString("HeatVisionDimension", heatVisionDimension);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        heatTrap = tag.getBoolean("HeatTrap");
        heatTrapPos = heatTrap ? tag.getLong("HeatTrapPos") : 0L;
        heatTrapDimension = heatTrap
                ? tag.getString("HeatTrapDimension") : "";
        grabbedTargetId = -1;
        blocking = false;
        heatVisionLeaseId = tag.hasUUID("HeatVisionLease")
                ? tag.getUUID("HeatVisionLease") : null;
        heatVisionExpiresAt = heatVisionLeaseId != null
                ? tag.getLong("HeatVisionExpiresAt") : 0L;
        heatVisionDimension = heatVisionLeaseId != null
                ? tag.getString("HeatVisionDimension") : "";
    }
}

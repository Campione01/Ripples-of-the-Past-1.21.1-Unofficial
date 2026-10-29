package rotp.addons.stone_legacy_zombie_orders.state;

import java.util.Optional;
import java.util.UUID;

import rotp.core.api.playerpower.PlayerPowerTransitions;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class ZombieConversionState
        implements INBTSerializable<CompoundTag> {
    private static final String OWNER_KEY = "Owner";
    private static final String PREVIOUS_POWER_KEY =
            "PreviousPower";
    private static final String NO_PREVIOUS_POWER_KEY =
            "NoPreviousPower";

    private UUID ownerUuid;
    private PlayerPowerTransitions.Snapshot previousPower;
    private boolean convertedFromNoPower;

    public Optional<UUID> ownerUuid() {
        return Optional.ofNullable(ownerUuid);
    }

    public Optional<PlayerPowerTransitions.Snapshot>
            previousPower() {
        return Optional.ofNullable(previousPower);
    }

    public boolean isConverted() {
        return previousPower != null || convertedFromNoPower;
    }

    public boolean convertedFromNoPower() {
        return convertedFromNoPower;
    }

    public void markConverted(
            UUID ownerUuid,
            PlayerPowerTransitions.Snapshot previousPower) {
        this.ownerUuid = ownerUuid;
        this.previousPower = previousPower;
        convertedFromNoPower = false;
    }

    public void markConvertedFromNoPower(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        previousPower = null;
        convertedFromNoPower = true;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void clear() {
        ownerUuid = null;
        previousPower = null;
        convertedFromNoPower = false;
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        if (ownerUuid != null) {
            tag.putUUID(OWNER_KEY, ownerUuid);
        }
        if (previousPower != null) {
            tag.put(
                    PREVIOUS_POWER_KEY,
                    previousPower.save());
        }
        if (convertedFromNoPower) {
            tag.putBoolean(NO_PREVIOUS_POWER_KEY, true);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        ownerUuid = tag.hasUUID(OWNER_KEY)
                ? tag.getUUID(OWNER_KEY)
                : null;
        previousPower = tag.contains(PREVIOUS_POWER_KEY)
                ? PlayerPowerTransitions.Snapshot.load(
                                tag.getCompound(
                                        PREVIOUS_POWER_KEY))
                        .orElse(null)
                : null;
        convertedFromNoPower = previousPower == null
                && tag.getBoolean(NO_PREVIOUS_POWER_KEY);
    }
}

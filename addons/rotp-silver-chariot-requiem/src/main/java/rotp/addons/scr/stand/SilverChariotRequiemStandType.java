package rotp.addons.scr.stand;

import com.google.gson.JsonElement;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class SilverChariotRequiemStandType
        extends EntityStandType {
    public SilverChariotRequiemStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        enforceAutonomousProfile();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforceAutonomousProfile();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforceAutonomousProfile();
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void enforceAutonomousProfile() {
        standControlType(StandControlType.AUTOMATIC);
        manualControl(false);
        distanceStrengthDecay(false);
        standLeapEnabled = false;
    }
}

package rotp.addons.aquanecklace.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class AquaNecklaceStandType
        extends EntityStandType {
    public AquaNecklaceStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        enforceRemoteControlProfile();
    }

    @Override
    public AquaNecklaceTrainingData newDataInstance() {
        return new AquaNecklaceTrainingData(this);
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforceRemoteControlProfile();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforceRemoteControlProfile();
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void enforceRemoteControlProfile() {
        standControlType(StandControlType.LONG_DISTANCE_OPERATION);
        manualControl(true);
        distanceStrengthDecay(false);
        standLeapEnabled = false;
    }
}

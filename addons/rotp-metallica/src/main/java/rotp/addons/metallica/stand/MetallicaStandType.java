package rotp.addons.metallica.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class MetallicaStandType extends EntityStandType {
    public MetallicaStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        enforcePhenomenonProfile();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforcePhenomenonProfile();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforcePhenomenonProfile();
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void enforcePhenomenonProfile() {
        standControlType(StandControlType.PHENOMENON);
        manualControl(false);
        distanceStrengthDecay(false);
        standLeapEnabled = false;
    }
}

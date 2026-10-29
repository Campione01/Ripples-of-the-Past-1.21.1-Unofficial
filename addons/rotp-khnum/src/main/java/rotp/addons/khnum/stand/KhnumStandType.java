package rotp.addons.khnum.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class KhnumStandType extends EntityStandType {
    public KhnumStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> controllerType,
            ResourceLocation id) {
        super(stats, moveset, controllerType, id);
        pinPhenomenonTopology();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        entityType.reset();
        pinPhenomenonTopology();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        pinPhenomenonTopology();
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void pinPhenomenonTopology() {
        standLeapEnabled = false;
    }
}

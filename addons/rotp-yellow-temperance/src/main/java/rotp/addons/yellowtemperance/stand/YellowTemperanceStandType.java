package rotp.addons.yellowtemperance.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class YellowTemperanceStandType extends EntityStandType {
    public YellowTemperanceStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> controllerType,
            ResourceLocation id) {
        super(stats, moveset, controllerType, id);
        pinPlayerAttachedTopology();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        entityType.reset();
        pinPlayerAttachedTopology();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        pinPlayerAttachedTopology();
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void pinPlayerAttachedTopology() {
        standLeapEnabled = false;
    }
}

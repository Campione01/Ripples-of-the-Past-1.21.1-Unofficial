package rotp.addons.thesun.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

public final class TheSunStandType extends EntityStandType {
    public TheSunStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        disableDirectControl();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        disableDirectControl();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        disableDirectControl();
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void disableDirectControl() {
        standLeapEnabled = false;
    }
}

package rotp.addons.tusk.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class TuskStandType extends EntityStandType {
    private final int actIndex;
    private final Vec3 followOffset;
    private final boolean fixedLeapEnabled;

    public TuskStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id,
            int actIndex,
            Vec3 followOffset,
            boolean fixedLeapEnabled) {
        super(stats, moveset, standEntityType, id);
        this.actIndex = actIndex;
        this.followOffset = followOffset;
        this.fixedLeapEnabled = fixedLeapEnabled;
        standLeapEnabled = fixedLeapEnabled;
    }

    public int actIndex() {
        return actIndex;
    }

    public Vec3 followOffset() {
        return followOffset;
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        standLeapEnabled = fixedLeapEnabled;
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        standLeapEnabled = fixedLeapEnabled;
    }

    @Override
    public boolean canLeap() {
        return fixedLeapEnabled;
    }
}

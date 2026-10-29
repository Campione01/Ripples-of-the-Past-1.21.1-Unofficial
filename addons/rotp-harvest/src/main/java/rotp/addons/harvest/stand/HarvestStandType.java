package rotp.addons.harvest.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.harvest.entity.HarvestStandEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class HarvestStandType extends EntityStandType {
    public static final String TOPOLOGY = "H4";

    public HarvestStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    // 1.16 HarvestStandType.unsummon :65-68 reset the swarm status after every
    // unsummon press, also one StandEntity.retractStand refused for a held
    // command, so the Harvest that stayed out dropped its attack or search.
    @Override
    public void unsummon(LivingEntity user, StandPower standPower) {
        super.unsummon(user, standPower);
        if (user != null && !user.level().isClientSide()) {
            HarvestStandData userData = HarvestStandData.of(standPower);
            if (userData != null) {
                userData.clearSwarmStatus();
            }
            if (standPower != null
                    && standPower.getSummonedStandEntity()
                            instanceof HarvestStandEntity coordinator) {
                coordinator.resetSwarmStatus();
            }
        }
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (standPower != null
                && standPower.getSummonedStandEntity()
                        instanceof HarvestStandEntity coordinator) {
            coordinator.cleanupDependents();
        }
        HarvestStandData data = HarvestStandData.of(standPower);
        // 1.16 StandPower.clear :186-188 unsummoned only a summoned Stand;
        // StandType.tickUser :273-275 unsummoned an unusable power each tick.
        if (data != null && user != null && !user.level().isClientSide()
                && (standPower.isSummoned() || !standPower.canUsePower())) {
            data.clearSwarmStatus();
        }
        super.forceUnsummon(user, standPower);
    }

    @Override
    public HarvestStandData newDataInstance() {
        return new HarvestStandData(this);
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        standLeapEnabled = false;
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        standLeapEnabled = false;
    }

    @Override
    public boolean canBeManuallyControlled() {
        return false;
    }

    @Override
    public boolean canLeap() {
        return false;
    }
}

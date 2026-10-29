package rotp.addons.speedking.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import com.google.gson.JsonElement;

import rotp.addons.speedking.SpeedKingService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class SpeedKingStandType extends EntityStandType {
    public SpeedKingStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
        enforceControlPolicy();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforceControlPolicy();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforceControlPolicy();
    }

    // 1.16 kept the heat trap on the Stand entity, so any unsummon
    // (plain, wrong level, power lost, replaced) dropped the trap too.
    @Override
    public void forceUnsummon(LivingEntity user, StandPower power) {
        SpeedKingService.onStandCleared(user);
        super.forceUnsummon(user, power);
    }

    // Removing the Stand (stand remove) drops the heat trap as well.
    @Override
    public StandTypePersistentData newDataInstance() {
        return new SpeedKingStandData(this);
    }

    private static final class SpeedKingStandData
            extends StandTypePersistentData {
        private SpeedKingStandData(SpeedKingStandType powerType) {
            super(powerType);
        }

        @Override
        public void onPowerCleared(
                Power<?> userPower, PowerType newType) {
            super.onPowerCleared(userPower, newType);
            SpeedKingService.onStandCleared(userPower.getUser());
        }
    }

    private void enforceControlPolicy() {
        standControlType(StandControlType.HYBRID_FORM);
        manualControl(true);
        distanceStrengthDecay(true);
    }
}

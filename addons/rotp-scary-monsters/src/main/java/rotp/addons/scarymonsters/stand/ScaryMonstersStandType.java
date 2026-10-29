package rotp.addons.scarymonsters.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.scarymonsters.ScaryMonstersService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class ScaryMonstersStandType extends EntityStandType {
    public ScaryMonstersStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
        enforcePolicy();
    }

    @Override
    public void applyConfig(JsonElement json) {
        super.applyConfig(json);
        enforcePolicy();
    }

    @Override
    public void restoreDefaults() {
        super.restoreDefaults();
        enforcePolicy();
    }

    @Override
    public void forceUnsummon(LivingEntity user, StandPower power) {
        if (user instanceof ServerPlayer player) {
            ScaryMonstersService.cleanupAll(player);
        }
        super.forceUnsummon(user, power);
    }

    private void enforcePolicy() {
        standControlType(StandControlType.HYBRID_FORM);
        manualControl(false);
        distanceStrengthDecay(false);
    }
}

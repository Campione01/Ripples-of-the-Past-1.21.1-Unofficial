package rotp.addons.jumpingjackflash.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.jumpingjackflash.state.JumpingJackFlashService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class JumpingJackFlashStandType extends EntityStandType {
    public JumpingJackFlashStandType(
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

    @Override
    public void forceUnsummon(LivingEntity user, StandPower power) {
        if (user instanceof ServerPlayer player) {
            JumpingJackFlashService.cleanupOwner(player);
        }
        super.forceUnsummon(user, power);
    }

    private void enforceControlPolicy() {
        standControlType(StandControlType.HYBRID_FORM);
        manualControl(true);
        distanceStrengthDecay(true);
    }
}

package rotp.addons.stonefree.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.stonefree.state.StoneFreeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class StoneFreeStandType extends EntityStandType {
    public StoneFreeStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> controllerType,
            ResourceLocation id) {
        super(stats, moveset, controllerType, id);
    }

    @Override
    protected float getBaseStaminaRegen(
            StandPower standPower) {
        LivingEntity user = standPower.getUser();
        if (user != null
                && user.hasData(
                        rotp.addons.stonefree
                                .StoneFreeAddon.USER_STATE)
                && StoneFreeState.get(user)
                        .activeUserStrings() > 0) {
            return 0.0F;
        }
        return super.getBaseStaminaRegen(standPower);
    }
}

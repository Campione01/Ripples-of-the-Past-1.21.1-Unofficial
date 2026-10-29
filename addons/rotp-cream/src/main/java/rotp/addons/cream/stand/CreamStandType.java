package rotp.addons.cream.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class CreamStandType extends EntityStandType {
    public CreamStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> entityType,
            ResourceLocation id) {
        super(stats, moveset, entityType, id);
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()
                && standPower.getSummonedStandEntity()
                        instanceof CreamEntity cream) {
            cream.endVoidMode(false);
        }
        super.forceUnsummon(user, standPower);
    }
}

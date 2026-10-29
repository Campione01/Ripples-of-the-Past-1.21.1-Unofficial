package rotp.addons.badcompany.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.badcompany.entity.BadCompanyStandEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class BadCompanyStandType extends EntityStandType {
    public static final String TOPOLOGY = "H3";

    public BadCompanyStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        disableStandLeap();
    }

    @Override
    protected boolean supportsColonyCoordinatorManualControl() {
        return true;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (user != null
                && user.getServer() != null
                && !user.level().isClientSide()) {
            BadCompanyStandEntity.cleanupOwnerDependents(
                    user.getServer(), user.getUUID());
        }
        super.forceUnsummon(user, standPower);
    }

    @Override
    public boolean canLeap() {
        return false;
    }

    private void disableStandLeap() {
        standLeapEnabled = false;
    }
}

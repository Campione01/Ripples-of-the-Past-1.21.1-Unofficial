package rotp.addons.catchtherainbow.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.catchtherainbow.entity.CatchTheRainbowEntity;
import rotp.addons.catchtherainbow.CatchTheRainbowCleanup;
import rotp.addons.catchtherainbow.item.CatchTheRainbowMaskItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class CatchTheRainbowStandType extends EntityStandType {
    public CatchTheRainbowStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    @Override
    public boolean onTrySummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()) {
            CatchTheRainbowMaskItem.purgeAll(user);
        }
        return super.onTrySummon(user, standPower);
    }

    @Override
    public boolean summon(
            LivingEntity user, StandPower standPower) {
        if (!super.summon(user, standPower)) {
            return false;
        }
        if (!user.level().isClientSide()) {
            CatchTheRainbowMaskItem.enforceEquipped(user);
        }
        return true;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()) {
            CatchTheRainbowEntity controller =
                    standPower.getSummonedStandEntity()
                            instanceof CatchTheRainbowEntity summoned
                            ? summoned : null;
            CatchTheRainbowCleanup.onUnsummon(user, controller);
        }
        super.forceUnsummon(user, standPower);
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

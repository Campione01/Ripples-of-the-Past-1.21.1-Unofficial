package rotp.addons.tickettoride.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.tickettoride.item.TearsBladeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TicketToRideStandType extends EntityStandType {
    public TicketToRideStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    @Override
    public boolean summon(
            LivingEntity user, StandPower standPower) {
        if (!super.summon(user, standPower)) {
            return false;
        }
        if (!user.level().isClientSide()
                && user instanceof Player player) {
            TearsBladeItem.enforceOwnedItem(player);
        }
        return true;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()) {
            TearsBladeItem.purgeAll(user);
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

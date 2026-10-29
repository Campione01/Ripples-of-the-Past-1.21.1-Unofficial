package rotp.addons.thelock.stand;

import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.thelock.item.TheLockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class TheLockStandType extends EntityStandType {
    public TheLockStandType(
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
            TheLockItem.purgeAll(user);
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
            if (user instanceof Player player) {
                TheLockItem.enforceOwnedItem(player);
            } else {
                ItemStack offhand = user.getOffhandItem();
                if (!offhand.isEmpty()) {
                    user.spawnAtLocation(offhand.copy());
                }
                user.setItemInHand(
                        InteractionHand.OFF_HAND,
                        TheLockItem.createOwned(user));
            }
        }
        return true;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (!user.level().isClientSide()) {
            TheLockItem.purgeAll(user);
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

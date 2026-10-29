package rotp.addons.frosttraveler.stand;

import java.util.function.Consumer;

import rotp.core.api.item.LivingHandUseBlockers;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class CreamStarterStandType extends EntityStandType {
    public CreamStarterStandType(
            StandStats stats,
            MovesetBuilder moveset,
            EntityType<? extends StandEntity> standEntityType,
            ResourceLocation id) {
        super(stats, moveset, standEntityType, id);
        standLeapEnabled = false;
    }

    // 1.16 GameplayHandler gave the Cream Starter on the first player tick
    // the Stand was out; every summon (an action's auto-summon too) adds the
    // Stand here, so the sprayer need not wait for the periodic reconcile.
    @Override
    public void finalizeStandSummonFromAction(
            LivingEntity user,
            StandPower standPower,
            StandEntity standEntity,
            boolean addToWorld) {
        super.finalizeStandSummonFromAction(
                user, standPower, standEntity, addToWorld);
        if (addToWorld
                && user instanceof Player player
                && !player.level().isClientSide()) {
            CreamStarterService.reconcile(player);
        }
    }

    // 1.16 Sticky Fingers StandTypeMixin: no summon with both arms
    // unzipped; every summon path (auto-summon too) ends here
    @Override
    public boolean summon(
            LivingEntity user,
            StandPower standPower,
            Consumer<StandEntity> beforeTheSummon,
            boolean addToWorld) {
        if (!standPower.isSummoned()
                && LivingHandUseBlockers.isBlocked(
                        user, InteractionHand.MAIN_HAND)
                && LivingHandUseBlockers.isBlocked(
                        user, InteractionHand.OFF_HAND)) {
            return false;
        }
        return super.summon(
                user, standPower, beforeTheSummon, addToWorld);
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (user instanceof Player player
                && !player.level().isClientSide()) {
            CreamStarterService.purgeOwnedSprayers(player);
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

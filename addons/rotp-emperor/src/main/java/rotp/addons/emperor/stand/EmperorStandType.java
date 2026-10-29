package rotp.addons.emperor.stand;

import java.util.function.Consumer;

import rotp.core.api.item.LivingHandUseBlockers;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.google.gson.JsonElement;

import rotp.addons.emperor.action.EmperorSummonAction;
import rotp.addons.emperor.entity.EmperorStandEntity;
import rotp.addons.emperor.item.EmperorItem;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class EmperorStandType extends EntityStandType {
    public EmperorStandType(
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
        if (!(user instanceof Player player)) {
            return false;
        }
        if (!player.level().isClientSide()) {
            EmperorItem.purgeAll(player);
        }
        if (!super.onTrySummon(user, standPower)) {
            return false;
        }
        // 1.16 Sticky Fingers StandTypeMixin refused silently
        if (armsUnzipped(player)) {
            return false;
        }
        if (gunHand(player) == null) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_zemperor.message.hands_full"),
                    true);
            return false;
        }
        return true;
    }

    @Override
    public boolean summon(
            LivingEntity user, StandPower standPower) {
        if (!(user instanceof Player player)
                || !super.summon(user, standPower)) {
            return false;
        }
        if (!player.level().isClientSide()) {
            InteractionHand hand = gunHand(player);
            if (hand == null) {
                forceUnsummon(user, standPower);
                return false;
            }
            player.setItemInHand(
                    hand, new ItemStack(
                            rotp.addons.emperor
                                    .EmperorAddon.EMPEROR_ITEM.get()));
            EmperorSummonAction.play(player);
        }
        return true;
    }

    // every summon path (auto-summon too) ends here, as in 1.16
    @Override
    public boolean summon(
            LivingEntity user,
            StandPower standPower,
            Consumer<StandEntity> beforeTheSummon,
            boolean addToWorld) {
        if (!standPower.isSummoned() && armsUnzipped(user)) {
            return false;
        }
        return super.summon(
                user, standPower, beforeTheSummon, addToWorld);
    }

    // Sticky Fingers unzips arms through the core hand-use blockers
    private static boolean armsUnzipped(LivingEntity user) {
        return LivingHandUseBlockers.isBlocked(
                        user, InteractionHand.MAIN_HAND)
                && LivingHandUseBlockers.isBlocked(
                        user, InteractionHand.OFF_HAND);
    }

    // an empty hand whose arm is still attached, main hand first
    private static InteractionHand gunHand(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).isEmpty()
                    && !LivingHandUseBlockers.isBlocked(player, hand)) {
                return hand;
            }
        }
        return null;
    }

    @Override
    public void forceUnsummon(
            LivingEntity user, StandPower standPower) {
        if (user instanceof Player player
                && !player.level().isClientSide()) {
            if (standPower.getSummonedStandEntity()
                    instanceof EmperorStandEntity controller) {
                controller.clearTarget();
            }
            EmperorItem.purgeAll(player);
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

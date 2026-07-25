package com.github.standobyte.jojoimpl.stands.goldexperience;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.init.ModItemDataComponents;
import com.github.standobyte.jojo.init.power.ModStands;
import com.github.standobyte.jojo.powersystem.ability.condition.ConditionCheck;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GEBodyTissueItem extends Item {
    public GEBodyTissueItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack item = player.getItemInHand(hand);
        ConditionCheck canUse = GoldExperienceHealAbility.checkCanHealWithBodyTissue(player);
        if (canUse.isPositive()) {
            if (!level.isClientSide()) {
                Optional<LivingEntity> userCreator = getGEUser((ServerLevel) level, item);
                if (userCreator == null) {
                    healAfterDelay(player, null);
                }
                else {
                    userCreator.map(StandPower::get)
                            .filter(userPower -> userPower != null && creatorPowerHasHealingItem(userPower))
                            .ifPresent(userPower -> healAfterDelay(player, userPower));
                }

                if (!player.getAbilities().instabuild) {
                    item.shrink(1);
                }
            }
            return InteractionResultHolder.sidedSuccess(item, level.isClientSide());
        }
        else {
            if (!level.isClientSide()) {
                ConditionCheck.sendActionFailedMessage(null, canUse, player);
            }
            return InteractionResultHolder.fail(item);
        }
    }

    private static void healAfterDelay(LivingEntity player, @Nullable StandPower geUserPower) {
        GoldExperienceHealAbility.applyGoldExperienceBodyTissueHeal(player, geUserPower);
    }

    public static void onCreated(StandPower userPower, ItemStack item) {
        if (userPower != null) {
            LivingEntity user = userPower.getUser();
            if (user != null) {
                item.set(ModItemDataComponents.GE_USER.get(), user.getUUID());
            }
        }
    }

    @Nullable
    private static Optional<LivingEntity> getGEUser(ServerLevel level, ItemStack item) {
        UUID id = item.get(ModItemDataComponents.GE_USER.get());
        if (id != null) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living) {
                return Optional.of(living);
            }
            return Optional.empty();
        }

        return null;
    }

    private static boolean creatorPowerHasHealingItem(StandPower userPower) {
        return userPower.getPowerType() == ModStands.GOLD_EXPERIENCE.get()
                && userPower.getCurTypeData() != null
                && !userPower.getCurTypeData()._lockedAbilities.contains("healing_item");
    }
}

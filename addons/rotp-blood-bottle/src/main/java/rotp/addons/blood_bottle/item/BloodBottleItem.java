package rotp.addons.blood_bottle.item;

import rotp.addons.blood_bottle.service.BloodBottlePowerService;
import rotp.addons.blood_bottle.service.BloodBottlePowerService.DrinkResult;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class BloodBottleItem extends Item {
    private static final int USE_DURATION = 16;
    private static final int NUTRITION = 2;
    private static final float SATURATION_MODIFIER = 10.0F;

    public BloodBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!BloodBottlePowerService.canDrink(player).allowed()) {
            return InteractionResultHolder.fail(stack);
        }
        return ItemUtils.startUsingInstantly(
                level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity living) {
        super.finishUsingItem(stack, level, living);
        if (!(living instanceof Player player)
                || level.isClientSide()) {
            return stack;
        }

        DrinkResult result =
                BloodBottlePowerService.drink(player);
        if (!result.applied()) {
            return stack;
        }

        player.getFoodData().eat(
                NUTRITION, SATURATION_MODIFIER);
        player.awardStat(Stats.ITEM_USED.get(this));
        if (player.getAbilities().instabuild) {
            return stack;
        }

        stack.shrink(1);
        ItemStack emptyBottle =
                new ItemStack(Items.GLASS_BOTTLE);
        if (stack.isEmpty()) {
            return emptyBottle;
        }
        if (!player.addItem(emptyBottle)) {
            player.spawnAtLocation(emptyBottle);
        }
        return stack;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }
}

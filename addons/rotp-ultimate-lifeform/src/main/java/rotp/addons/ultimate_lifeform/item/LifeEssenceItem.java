package rotp.addons.ultimate_lifeform.item;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
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

public final class LifeEssenceItem extends Item {
    public LifeEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity user) {
        Player player =
                user instanceof Player value ? value : null;
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(
                    serverPlayer, stack);
        }
        if (!level.isClientSide()) {
            user.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects
                                    .REGENERATION,
                            600,
                            4));
            user.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects
                                    .ABSORPTION,
                            600,
                            4));
            user.addEffect(
                    new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects
                                    .HEALTH_BOOST,
                            120000,
                            2));
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        if (player == null
                || !player.getAbilities().instabuild) {
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            if (player != null) {
                ItemStack bottle =
                        new ItemStack(Items.GLASS_BOTTLE);
                if (!player.getInventory().add(bottle)) {
                    player.drop(bottle, false);
                }
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {
        return ItemUtils.startUsingInstantly(
                level, player, hand);
    }
}

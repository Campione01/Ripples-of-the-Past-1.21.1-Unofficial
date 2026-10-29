package rotp.addons.tusk.item;

import rotp.addons.tusk.TuskAddon;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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

public final class TeaItem extends Item {
    public TeaItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack, Level level, LivingEntity living) {
        super.finishUsingItem(stack, level, living);
        if (!level.isClientSide()
                && living instanceof Player player
                && TuskAddon.isTuskUser(player)
                && TuskAddon.nailState(player).addNail(TuskAddon.nailRegenTicks(player))) {
            TuskAddon.syncNailState(player);
        }

        if (living instanceof Player player
                && player.getAbilities().instabuild) {
            return stack;
        }

        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
        stack.shrink(1);
        if (stack.isEmpty()) {
            return bottle;
        }
        if (living instanceof Player player) {
            if (!player.addItem(bottle)) {
                player.spawnAtLocation(bottle);
            }
        } else {
            living.spawnAtLocation(bottle);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.HONEY_DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }
}

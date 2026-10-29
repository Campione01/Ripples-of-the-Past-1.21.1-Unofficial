package rotp.addons.scarymonsters.item;

import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public final class CoffeeItem extends Item {
    public CoffeeItem(Properties properties) {
        super(properties);
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
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            StandPower power = StandPower.get(entity);
            if (power != null) {
                power.setStamina(power.getMaxStamina());
            }
            level.playSound(
                    null,
                    entity.blockPosition(),
                    ScaryMonstersAddon.COFFEE_SOUND.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F);
        }
        return entity instanceof ServerPlayer
                ? new ItemStack(ScaryMonstersAddon.COFFEE_EMPTY.get())
                : stack;
    }
}

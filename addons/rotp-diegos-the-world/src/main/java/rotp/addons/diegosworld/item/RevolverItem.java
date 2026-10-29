package rotp.addons.diegosworld.item;

import java.util.List;

import rotp.addons.diegosworld.DiegosTheWorldAddon;
import rotp.addons.diegosworld.entity.DiegoRevolverBulletEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class RevolverItem extends Item {
    public static final int MAX_AMMO = 6;
    public static final int USE_DURATION = 85;
    private static final String AMMO_TAG = "Ammo";

    public RevolverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            int loaded = reloadableAmmo(player, stack);
            if (loaded <= 0) {
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide()) {
                consumeReloadMaterials(player, loaded);
                setAmmo(stack, getAmmo(stack) + loaded);
                player.getCooldowns().addCooldown(this, loaded * 2);
            }
            return InteractionResultHolder.sidedSuccess(
                    stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(
            Level level,
            LivingEntity entity,
            ItemStack stack,
            int remainingTicks) {
        if (remainingTicks <= 1) {
            entity.releaseUsingItem();
            return;
        }
        int modulo = remainingTicks % 43;
        boolean shotTick =
                modulo == 0
                        || modulo == 7
                        || modulo == 14
                        || modulo == 21
                        || modulo == 28
                        || modulo == 35
                        || modulo == 42;
        if (!shotTick) {
            return;
        }

        int ammo = getAmmo(stack);
        if (ammo <= 0) {
            if (!level.isClientSide()) {
                level.playSound(
                        null,
                        entity.blockPosition(),
                        DiegosTheWorldAddon
                                .REVOLVER_NO_AMMO.get(),
                        entity.getSoundSource(),
                        1.0F,
                        1.0F);
                entity.releaseUsingItem();
            }
            return;
        }

        if (!level.isClientSide()) {
            DiegoRevolverBulletEntity bullet =
                    new DiegoRevolverBulletEntity(entity, level);
            bullet.shootFromRotation(entity, 20.0F, 0.0F);
            level.addFreshEntity(bullet);
            setAmmo(stack, ammo - 1);
            level.playSound(
                    null,
                    entity.blockPosition(),
                    DiegosTheWorldAddon.REVOLVER_SHOT.get(),
                    entity.getSoundSource(),
                    1.0F,
                    1.0F + (entity.getRandom().nextFloat() - 0.5F)
                            * 0.3F);
        }
        if (level.isClientSide() && entity instanceof Player) {
            float recoil =
                    1.0F + Math.min(
                            (1.0F
                                            - (float) remainingTicks
                                                    / USE_DURATION)
                                    * 6.0F,
                            3.0F);
            entity.setYRot(
                    entity.getYRot()
                            + (entity.getRandom().nextFloat() - 0.5F)
                                    * 0.3F * recoil);
            entity.setXRot(
                    entity.getXRot()
                            - entity.getRandom().nextFloat()
                                    * 0.75F * recoil);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(
            ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAmmo(stack) < MAX_AMMO;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(
                13.0F * getAmmo(stack) / (float) MAX_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB0B0B0;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.rotp_theworld.revolver.reload_prompt",
                        Component.keybind("key.sneak"),
                        Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
                        "item.rotp_theworld.revolver.reference_quote")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC));
    }

    public static int getAmmo(ItemStack stack) {
        int ammo = stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY)
                .copyTag()
                .getInt(AMMO_TAG);
        return Math.max(0, Math.min(ammo, MAX_AMMO));
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(
                        AMMO_TAG,
                        Math.max(0, Math.min(ammo, MAX_AMMO))));
    }

    private static int reloadableAmmo(
            Player player, ItemStack revolver) {
        int wanted = MAX_AMMO - getAmmo(revolver);
        if (wanted <= 0) {
            return 0;
        }
        if (player.getAbilities().instabuild) {
            return wanted;
        }
        int iron = countItem(player, Items.IRON_NUGGET);
        int gunpowder = countItem(player, Items.GUNPOWDER);
        return Math.min(wanted, Math.min(iron, gunpowder));
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        for (int i = 0;
                i < player.getInventory().getContainerSize();
                i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeReloadMaterials(
            Player player, int count) {
        if (player.getAbilities().instabuild) {
            return;
        }
        shrinkItem(player, Items.IRON_NUGGET, count);
        shrinkItem(player, Items.GUNPOWDER, count);
    }

    private static void shrinkItem(
            Player player, Item item, int count) {
        int remaining = count;
        for (int i = 0;
                i < player.getInventory().getContainerSize()
                        && remaining > 0;
                i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) {
                continue;
            }
            int consumed = Math.min(stack.getCount(), remaining);
            stack.shrink(consumed);
            remaining -= consumed;
        }
    }
}

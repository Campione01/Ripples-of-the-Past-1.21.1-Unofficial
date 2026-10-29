package rotp.addons.d4c.item;

import java.util.List;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.entity.D4CColtBulletEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class D4CColtItem extends Item {
    private static final String AMMO = "rotp_d4c_ammo";
    private static final String RELOADING = "rotp_d4c_reloading";
    private static final String SHOOTING = "rotp_d4c_shooting";

    public D4CColtItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isReloading(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        int ammo = ammo(stack);
        if (player.isShiftKeyDown() || ammo <= 0) {
            int needed = D4CPhase2BSpec.COLT_AMMO - ammo;
            if (ammo <= 0) {
                needed = D4CPhase2BSpec.COLT_AMMO;
            }
            if (!hasPaper(player, needed)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(
                            "message.rotp_d4c.colt_reload_failed"), true);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (!level.isClientSide()) {
                consumePaper(player, needed);
                setReloading(stack, D4CPhase2BSpec.COLT_RELOAD_TICKS);
                level.playSound(
                        null,
                        player.blockPosition(),
                        D4CAddon.COLT_RELOAD.get(),
                        SoundSource.PLAYERS,
                        0.7F,
                        1.0F);
            }
            return InteractionResultHolder.sidedSuccess(
                    stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft) {
        if (!(entity instanceof Player player)
                || isReloading(stack)
                || ammo(stack) <= 0) {
            return;
        }
        if (!level.isClientSide()) {
            D4CColtBulletEntity bullet =
                    new D4CColtBulletEntity(player, level);
            Vec3 direction = player.getLookAngle().normalize();
            Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D))
                    .normalize();
            boolean right = player.getMainArm()
                    == net.minecraft.world.entity.HumanoidArm.RIGHT;
            double sideOffset = right ? 0.6D : -0.6D;
            bullet.setPos(player.getEyePosition()
                    .add(direction.scale(0.9D))
                    .add(side.scale(sideOffset))
                    .add(0.0D, -0.4D, 0.0D));
            bullet.setDeltaMovement(direction.scale(3.0D));
            level.addFreshEntity(bullet);
            setAmmo(stack, ammo(stack) - 1);
            setShooting(stack, 4);
            level.playSound(
                    null,
                    player.blockPosition(),
                    D4CAddon.COLT_SHOOT.get(),
                    SoundSource.PLAYERS,
                    0.8F,
                    0.7F + player.getRandom().nextFloat() * 0.3F);
        }
    }

    @Override
    public void inventoryTick(
            ItemStack stack,
            Level level,
            Entity entity,
            int slot,
            boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide()) {
            return;
        }
        int reload = reloadingTicks(stack);
        if (reload > 0) {
            reload--;
            if (reload == 0) {
                setAmmo(stack, D4CPhase2BSpec.COLT_AMMO);
                clearInt(stack, RELOADING);
                level.playSound(
                        null,
                        entity.blockPosition(),
                        D4CAddon.COLT_RELOAD_ON.get(),
                        SoundSource.PLAYERS,
                        0.6F,
                        1.1F);
            } else {
                setInt(stack, RELOADING, reload);
            }
        }
        int shooting = getInt(stack, SHOOTING);
        if (shooting > 0) {
            if (--shooting == 0) {
                clearInt(stack, SHOOTING);
            } else {
                setInt(stack, SHOOTING, shooting);
            }
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 7_200;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (isReloading(stack)) {
            return Math.round(13.0F
                    * (D4CPhase2BSpec.COLT_RELOAD_TICKS
                            - reloadingTicks(stack))
                    / D4CPhase2BSpec.COLT_RELOAD_TICKS);
        }
        return Math.round(13.0F * ammo(stack)
                / D4CPhase2BSpec.COLT_AMMO);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (isReloading(stack)) {
            return 0xFF6600;
        }
        int ammo = ammo(stack);
        return ammo > 2 ? 0x00FF00
                : ammo > 0 ? 0xFFFF00 : 0xFF0000;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.rotp_d4c.colt_custom.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    public static int ammo(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.copyTag().contains(AMMO)) {
            setAmmo(stack, D4CPhase2BSpec.COLT_AMMO);
            return D4CPhase2BSpec.COLT_AMMO;
        }
        return Math.max(0, Math.min(
                D4CPhase2BSpec.COLT_AMMO,
                data.copyTag().getInt(AMMO)));
    }

    public static float reloadProgress(ItemStack stack) {
        int ticks = reloadingTicks(stack);
        return ticks <= 0
                ? 0.0F
                : 1.0F - (float) ticks
                        / D4CPhase2BSpec.COLT_RELOAD_TICKS;
    }

    public static float shootingProgress(ItemStack stack) {
        int ticks = getInt(stack, SHOOTING);
        return ticks > 2 ? 0.5F : ticks > 0 ? 1.0F : 0.0F;
    }

    private static void setAmmo(ItemStack stack, int ammo) {
        setInt(stack, AMMO, Math.max(0, Math.min(
                D4CPhase2BSpec.COLT_AMMO, ammo)));
    }

    private static boolean isReloading(ItemStack stack) {
        return reloadingTicks(stack) > 0;
    }

    private static int reloadingTicks(ItemStack stack) {
        return Math.max(0, getInt(stack, RELOADING));
    }

    private static void setReloading(ItemStack stack, int ticks) {
        setInt(stack, RELOADING, ticks);
    }

    private static void setShooting(ItemStack stack, int ticks) {
        setInt(stack, SHOOTING, ticks);
    }

    private static int getInt(ItemStack stack, String key) {
        return stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY)
                .copyTag()
                .getInt(key);
    }

    private static void setInt(
            ItemStack stack, String key, int value) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.putInt(key, value));
    }

    private static void clearInt(ItemStack stack, String key) {
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> tag.remove(key));
    }

    private static boolean hasPaper(Player player, int count) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int found = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.PAPER)) {
                found += stack.getCount();
            }
        }
        return found >= count;
    }

    private static void consumePaper(Player player, int count) {
        if (player.getAbilities().instabuild) {
            return;
        }
        int remaining = count;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(Items.PAPER)) {
                int consumed = Math.min(stack.getCount(), remaining);
                stack.shrink(consumed);
                remaining -= consumed;
            }
        }
    }
}

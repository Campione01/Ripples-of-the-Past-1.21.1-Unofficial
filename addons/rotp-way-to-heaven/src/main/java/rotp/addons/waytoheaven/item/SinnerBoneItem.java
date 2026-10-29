package rotp.addons.waytoheaven.item;

import java.util.List;

import rotp.addons.waytoheaven.WayToHeavenAddon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class SinnerBoneItem extends Item {
    public static final int MAX_SINNERS = 36;

    public SinnerBoneItem(Properties properties) {
        super(properties
                .stacksTo(1)
                .component(
                        WayToHeavenAddon.SINNERS.get(),
                        0));
    }

    public static int sinners(ItemStack stack) {
        if (!stack.is(WayToHeavenAddon.BONE.get())) {
            return 0;
        }
        return Mth.clamp(
                stack.getOrDefault(
                        WayToHeavenAddon.SINNERS.get(),
                        0),
                0,
                MAX_SINNERS);
    }

    public static void setSinners(
            ItemStack stack, int sinners) {
        if (stack.is(WayToHeavenAddon.BONE.get())) {
            stack.set(
                    WayToHeavenAddon.SINNERS.get(),
                    Mth.clamp(
                            sinners, 0, MAX_SINNERS));
        }
    }

    public static boolean isFull(ItemStack stack) {
        return stack.is(WayToHeavenAddon.BONE.get())
                && sinners(stack) >= MAX_SINNERS;
    }

    public static boolean incrementFirst(Player player) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (!stack.is(WayToHeavenAddon.BONE.get())) {
                continue;
            }
            int sinners = sinners(stack);
            if (sinners >= MAX_SINNERS) {
                return false;
            }
            setSinners(stack, sinners + 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return sinners(stack) < MAX_SINNERS;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(
                13.0F * sinners(stack) / MAX_SINNERS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float progress =
                (float) sinners(stack) / MAX_SINNERS;
        return Mth.hsvToRgb(
                Math.max(0.0F, progress) / 3.0F,
                1.0F,
                1.0F);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "item.rotp_waytoheaven.bone.sinners",
                        sinners(stack),
                        MAX_SINNERS)
                .withStyle(ChatFormatting.GRAY));
    }
}

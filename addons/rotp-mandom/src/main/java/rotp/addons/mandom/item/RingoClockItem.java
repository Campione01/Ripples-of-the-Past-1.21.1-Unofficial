package rotp.addons.mandom.item;

import java.util.List;

import rotp.addons.mandom.MandomRollbackGate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class RingoClockItem extends Item {
    public static final int MAX_CRACKS = 255;
    public static final int REWIND_SECONDS =
            MandomRollbackGate.REWIND_TICKS / 20;

    public RingoClockItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable(
                        "tooltip.rotp_mandom.ringo_clock",
                        REWIND_SECONDS,
                        REWIND_SECONDS)
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable(
                        "item.rotp_mandom.ringo_clock.reference_quote")
                .withStyle(
                        ChatFormatting.GRAY,
                        ChatFormatting.ITALIC));
    }
}

package rotp.addons.d4c.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class SaintCorpsePartItem extends Item {
    private static final String CURIOS_HINT =
            "item.rotp_d4c.saint_corpse.curios_hint";
    private static final String GENERAL =
            "item.rotp_d4c.saint_corpse.tooltip.general";

    public SaintCorpsePartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        appendCorpseTooltip(getDescriptionId(), tooltip);
    }

    // 1.16 ab.addInformation: curios hint, flavour lines, effect, general
    public static void appendCorpseTooltip(
            String itemKey, List<Component> tooltip) {
        boolean complete = itemKey.endsWith(".complete_saint_corpse");
        boolean xray = complete || itemKey.endsWith(".saint_corpse_eyes");
        String prefix = itemKey + ".tooltip.";
        tooltip.add(Component.empty());
        addRows(tooltip, CURIOS_HINT, ChatFormatting.AQUA);
        tooltip.add(Component.empty());
        addRows(tooltip, prefix + "line1", ChatFormatting.GOLD);
        addRows(tooltip, prefix + "line2", ChatFormatting.YELLOW);
        addRows(tooltip, prefix + "line3", complete
                ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY);
        if (complete) {
            addRows(tooltip, prefix + "line4", ChatFormatting.AQUA);
        }
        addRows(tooltip, prefix + "effect", ChatFormatting.GREEN);
        if (xray) {
            addRows(tooltip, itemKey + ".tooltip2",
                    ChatFormatting.GRAY, ChatFormatting.ITALIC);
        }
        tooltip.add(Component.empty());
        addRows(tooltip, GENERAL,
                ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC);
    }

    // one tooltip row per '\n'; effect texts keep the stamina row after one
    private static void addRows(
            List<Component> tooltip, String key, ChatFormatting... style) {
        String text = Component.translatable(key).getString();
        if (text.indexOf('\n') < 0) {
            tooltip.add(Component.translatable(key).withStyle(style));
            return;
        }
        for (String row : text.split("\n")) {
            tooltip.add(Component.literal(row).withStyle(style));
        }
    }
}

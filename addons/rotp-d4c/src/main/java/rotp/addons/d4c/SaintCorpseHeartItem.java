package rotp.addons.d4c;

import java.util.List;

import rotp.addons.d4c.item.SaintCorpsePartItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class SaintCorpseHeartItem extends Item {
    public SaintCorpseHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag) {
        // same layout as the other parts (1.16 shared one item class)
        SaintCorpsePartItem.appendCorpseTooltip(
                getDescriptionId(), tooltip);
    }
}

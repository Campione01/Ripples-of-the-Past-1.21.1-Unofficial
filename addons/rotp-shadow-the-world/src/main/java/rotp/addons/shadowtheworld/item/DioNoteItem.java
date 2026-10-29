package rotp.addons.shadowtheworld.item;

import net.minecraft.world.item.ItemStack;

public final class DioNoteItem extends AbstractDioNoteItem {
    public DioNoteItem(Properties properties) {
        super(properties, true);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}

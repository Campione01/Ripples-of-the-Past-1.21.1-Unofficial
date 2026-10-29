package rotp.addons.d4c;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class D4CCuriosAdapter {
    public static final String SAINT_CORPSE_SLOT = "saint_corpse";
    public static final int HEART_REGENERATION_TICKS =
            D4CPhase2BSpec.EFFECT_REFRESH_TICKS;
    public static final int HEART_REGENERATION_AMPLIFIER = 2;

    private D4CCuriosAdapter() {}

    public static void registerSaintCorpseItems(Item... items) {
        for (Item item : items) {
            CuriosApi.registerCurio(item, new SaintCorpseBehavior());
        }
    }

    private static final class SaintCorpseBehavior
            implements ICurioItem {
        @Override
        public boolean canEquip(SlotContext slotContext, ItemStack stack) {
            return SAINT_CORPSE_SLOT.equals(slotContext.identifier());
        }

        @Override
        public boolean canEquipFromUse(
                SlotContext slotContext,
                ItemStack stack) {
            return canEquip(slotContext, stack);
        }

    }
}

package rotp.addons.sexpistols.registry;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.content.accessory.MistaHatItem;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(SexPistolsIds.MOD_ID);

    public static final DeferredItem<Item> FOUR_ATTACK_ICON =
            ITEMS.registerSimpleItem(SexPistolsIds.FOUR_ATTACK_ICON_PATH);

    public static final DeferredItem<RevolverItem> REVOLVER =
            ITEMS.registerItem(
                    SexPistolsIds.REVOLVER_PATH,
                    RevolverItem::new,
                    new Item.Properties());

    public static final DeferredItem<Item> REVOLVER_AMMO =
            ITEMS.registerSimpleItem(
                    SexPistolsIds.REVOLVER_AMMO_PATH);

    public static final DeferredItem<Item> REVOLVER_CASING =
            ITEMS.registerSimpleItem(
                    SexPistolsIds.REVOLVER_CASING_PATH);

    public static final DeferredItem<MistaHatItem> MISTA_SUIT_HAT =
            ITEMS.register(
                    SexPistolsIds.MISTA_SUIT_HAT_PATH,
                    () -> new MistaHatItem(
                            new Item.Properties()
                                    .durability(
                                            ArmorItem.Type.HELMET
                                                    .getDurability(16))
                                    .component(
                                            SexPistolsDataComponents
                                                    .MISTA_HAT_AMMO.get(),
                                            0)));

    private SexPistolsItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}

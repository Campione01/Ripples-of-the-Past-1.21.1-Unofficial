package rotp.addons.tournament.item;

import rotp.addons.tournament.AddonMain;
import rotp.addons.tournament.init.AddonBlocks;
import rotp.core.init.ModItems;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AddonItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AddonMain.MOD_ID);
    public static final ResourceKey<JukeboxSong> ASSGORE_SONG =
            ResourceKey.create(
                    Registries.JUKEBOX_SONG,
                    AddonMain.id("assgore_record"));

    public static final DeferredItem<BlockItem> SPIKES =
            ITEMS.register(
                    "spikes",
                    () -> new BlockItem(
                            AddonBlocks.SPIKES.get(),
                            new Item.Properties()));
    public static final DeferredItem<BlockItem> TILE =
            ITEMS.register(
                    "tile",
                    () -> new BlockItem(
                            AddonBlocks.TILE.get(),
                            new Item.Properties()));
    public static final DeferredItem<BlockItem> LIGHT_BULB =
            ITEMS.register(
                    "light_bulb",
                    () -> new BlockItem(
                            AddonBlocks.LIGHT_BULB.get(),
                            new Item.Properties()));
    public static final DeferredItem<BlockItem> FAKE_SPIKES =
            ITEMS.register(
                    "fake_spikes",
                    () -> new BlockItem(
                            AddonBlocks.FAKE_SPIKES.get(),
                            new Item.Properties()));
    public static final DeferredItem<Item> ASSGORE_RECORD =
            ITEMS.register(
                    "assgore_disc",
                    () -> new Item(
                            new Item.Properties()
                                    .jukeboxPlayable(ASSGORE_SONG)
                                    .rarity(Rarity.RARE)
                                    .stacksTo(1)));

    private AddonItems() {}

    public static void buildCreativeTab(
            BuildCreativeModeTabContentsEvent event) {
        if (!ModItems.MAIN_TAB.getKey().equals(event.getTabKey())) {
            return;
        }
        event.accept(SPIKES.get());
        event.accept(TILE.get());
        event.accept(LIGHT_BULB.get());
        event.accept(FAKE_SPIKES.get());
    }
}

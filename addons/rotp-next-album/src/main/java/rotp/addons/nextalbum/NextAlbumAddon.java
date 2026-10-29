package rotp.addons.nextalbum;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.item.ItemHandFreePredicates;
import rotp.core.init.ModItems;

import rotp.addons.nextalbum.effect.CollapseEffect;
import rotp.addons.nextalbum.item.AjaStoneNecklaceItem;
import rotp.addons.nextalbum.item.LuckPluckItem;
import rotp.addons.nextalbum.item.MistaSuitArmorItem;
import rotp.addons.nextalbum.item.MistaSuitArmorMaterial;
import rotp.addons.nextalbum.network.NecklaceBeamUsePayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NextAlbumIds.MOD_ID)
public final class NextAlbumAddon {
    public static final int REQUIRED_CORE_ABI = 1;

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(
                    Registries.MOB_EFFECT, NextAlbumIds.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(NextAlbumIds.MOD_ID);

    public static final DeferredHolder<MobEffect, CollapseEffect> COLLAPSE =
            MOB_EFFECTS.register(
                    NextAlbumIds.COLLAPSE_PATH,
                    () -> new CollapseEffect(
                            MobEffectCategory.HARMFUL,
                            0x6F6F7A));
    public static final DeferredHolder<Item, LuckPluckItem> LUCK_PLUCK =
            ITEMS.register(
                    NextAlbumIds.LUCK_PLUCK_PATH,
                    () -> new LuckPluckItem(
                            new Item.Properties()
                                    .rarity(Rarity.RARE)));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_CLOTH = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_CLOTH_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.CHESTPLATE,
                            "geo/mista_suit_cloth.geo.json",
                            "textures/models/armor/mista_suit_cloth.png",
                            "geo/mista_suit_cloth_slim.geo.json",
                            "textures/models/armor/"
                                    + "mista_suit_cloth_slim.png",
                            true,
                            true,
                            false));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_PANTS = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_PANTS_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.LEGGINGS,
                            "geo/mista_suit_pants.geo.json",
                            "textures/models/armor/mista_suit_pants.png",
                            null,
                            null,
                            false,
                            false,
                            true));
    public static final DeferredHolder<Item, MistaSuitArmorItem>
            MISTA_SUIT_BOOTS = ITEMS.register(
                    NextAlbumIds.MISTA_SUIT_BOOTS_PATH,
                    () -> new MistaSuitArmorItem(
                            ArmorItem.Type.BOOTS,
                            "geo/mista_suit_boots.geo.json",
                            "textures/models/armor/mista_suit_boots.png",
                            null,
                            null,
                            false,
                            false,
                            true));
    public static final DeferredHolder<Item, AjaStoneNecklaceItem>
            AJA_STONE_NECKLACE = ITEMS.register(
                    NextAlbumIds.AJA_STONE_NECKLACE_PATH,
                    () -> new AjaStoneNecklaceItem(
                            new Item.Properties()
                                    .stacksTo(1)
                                    .durability(
                                            ArmorItem.Type.HELMET
                                                    .getDurability(5))
                                    .rarity(Rarity.RARE)));

    public NextAlbumAddon(IEventBus modEventBus) {
        requireCoreApi();
        ItemHandFreePredicates.register(
                NextAlbumIds.id(NextAlbumIds.LUCK_PLUCK_PATH),
                stack -> stack.getItem() instanceof LuckPluckItem luckPluck
                        && luckPluck.openFingers());
        MOB_EFFECTS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(NecklaceBeamUsePayload::register);
        modEventBus.addListener(this::addCreativeTabContents);
    }

    private void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModItems.MAIN_TAB.getKey()) {
            event.accept(LUCK_PLUCK.get());
            event.accept(MISTA_SUIT_CLOTH.get());
            event.accept(MISTA_SUIT_PANTS.get());
            event.accept(MISTA_SUIT_BOOTS.get());
            event.accept(AJA_STONE_NECKLACE.get());
        }
    }

    private static void requireCoreApi() {
        if (!RotpAddonApi.supportsAbi(REQUIRED_CORE_ABI)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_ABILITY_RESOURCE_NAMESPACE_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi.FEATURE_STAND_VISUAL_CONTEXT_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_ITEM_HAND_FREE_PREDICATES_V1)
                || !RotpAddonApi.supportsFeature(
                        RotpAddonApi
                                .FEATURE_SERVER_HELD_INPUT_CONTROL_V1)) {
            throw new IllegalStateException(
                    "RotP:NextAlbum requires ROTP addon ABI 1 with namespaced "
                            + "ability resources, stand visual contexts, "
                            + "item hand-free predicates, and server held-input "
                            + "control.");
        }
    }
}

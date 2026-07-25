package com.github.standobyte.jojo.init;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.github.standobyte.jojo.DebugItem;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.core.JojoRegistries;
import com.github.standobyte.jojo.item.AjaStoneItem;
import com.github.standobyte.jojo.item.BladeHatItem;
import com.github.standobyte.jojo.item.BreathControlMaskItem;
import com.github.standobyte.jojo.item.BubbleGlovesItem;
import com.github.standobyte.jojo.item.CassetteBlankItem;
import com.github.standobyte.jojo.item.CassetteRecordedItem;
import com.github.standobyte.jojo.item.ClackersItem;
import com.github.standobyte.jojo.item.GlovesItem;
import com.github.standobyte.jojo.item.KnifeItem;
import com.github.standobyte.jojo.item.MolotovItem;
import com.github.standobyte.jojo.item.OilItem;
import com.github.standobyte.jojo.item.PhotoItem;
import com.github.standobyte.jojo.item.PolaroidItem;
import com.github.standobyte.jojo.item.RoadRollerItem;
import com.github.standobyte.jojo.item.SatiporojaScarfItem;
import com.github.standobyte.jojo.item.SoapItem;
import com.github.standobyte.jojo.item.SledgehammerItem;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.github.standobyte.jojo.item.SuperAjaStoneItem;
import com.github.standobyte.jojo.item.TommyGunItem;
import com.github.standobyte.jojo.item.WalkmanItem;
import com.github.standobyte.jojo.mechanics.clothes.ClothesItem;
import com.github.standobyte.jojo.mechanics.clothes.itemdata.ClothesDataComponent;
import com.github.standobyte.jojo.mechanics.clothes.itemdata.ClothesPiece;
import com.github.standobyte.jojo.mechanics.clothes.itemdata.ClothesSlotType;
import com.github.standobyte.jojo.mechanics.clothes.mannequin.MannequinItem;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowItem;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowShardItem;
import com.github.standobyte.jojo.mechanics.standdisc.StandDiscItem;
import com.github.standobyte.jojo.mrpresident.MrPresidentKeyItem;
import com.github.standobyte.jojo.powersystem.standpower.StandInstance;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.subsystems.StoryPart;
import com.github.standobyte.jojo.tmp.charactertest.CharacterTestItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// TODO datagen for crafting recipes, advancements and loot tables
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ModItems {
	public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JojoMod.MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JojoMod.MOD_ID);

	public static final DeferredItem<Item> DEBUG_ITEM = ITEMS.registerItem("debug_item", DebugItem::new, new Item.Properties());
	public static final DeferredItem<Item> CHARACTER_TEST = ITEMS.registerItem("character_test", CharacterTestItem::new, new Item.Properties());

	public static final DeferredItem<Item> STAND_DISC = ITEMS.registerItem("stand_disc", StandDiscItem::new, new Item.Properties().stacksTo(1));

	public static final DeferredItem<BlockItem> SEWING_MACHINE = ITEMS.registerSimpleBlockItem("sewing_machine", 
			ModBlocks.SEWING_MACHINE, new Item.Properties());
	public static final Map<DyeColor, DeferredItem<BlockItem>> WOODEN_COFFIN_OAK = registerWoodenCoffinItems();

	public static final DeferredItem<Item> SEWING_NEEDLE = ITEMS.registerSimpleItem("sewing_needle");
	public static final DeferredItem<Item> KNIFE = ITEMS.registerItem("knife", KnifeItem::new, new Item.Properties().stacksTo(16));
	public static final DeferredItem<Item> SLEDGEHAMMER = ITEMS.registerItem("sledgehammer", SledgehammerItem::new, new Item.Properties());
	public static final DeferredItem<BladeHatItem> BLADE_HAT = ITEMS.registerItem("blade_hat", BladeHatItem::new,
			new Item.Properties().durability(ModArmorMaterials.BLADE_HAT_DURABILITY));
	public static final DeferredItem<Item> CLACKERS = ITEMS.registerItem("clackers", ClackersItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> TOMMY_GUN = ITEMS.registerItem("tommy_gun", TommyGunItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> ROAD_ROLLER = ITEMS.registerItem("road_roller", RoadRollerItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> POLAROID = ITEMS.registerItem("polaroid", PolaroidItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> PHOTO = ITEMS.registerItem("photo", PhotoItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> WALKMAN = ITEMS.registerItem("walkman", WalkmanItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> CASSETTE_BLANK = ITEMS.registerItem("cassette_blank", CassetteBlankItem::new, new Item.Properties().stacksTo(16));
	public static final DeferredItem<Item> CASSETTE_RECORDED = ITEMS.registerItem("cassette_recorded", CassetteRecordedItem::new, new Item.Properties().stacksTo(16));
	public static final DeferredItem<Item> OIL = ITEMS.registerItem("oil", OilItem::new, new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> MOLOTOV = ITEMS.registerItem("molotov", MolotovItem::new, new Item.Properties().stacksTo(16));
	public static final DeferredItem<Item> AJA_STONE = ITEMS.registerItem("aja_stone",
			AjaStoneItem::new, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	public static final DeferredItem<Item> SUPER_AJA_STONE = ITEMS.registerItem("super_aja_stone",
			SuperAjaStoneItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).durability(640));
	public static final DeferredItem<Item> GLOVES = ITEMS.registerItem("gloves",
			GlovesItem::new, new Item.Properties().stacksTo(1).durability(128));
	public static final DeferredItem<Item> BUBBLE_GLOVES = ITEMS.registerItem("bubble_gloves",
			BubbleGlovesItem::new, new Item.Properties().stacksTo(1).durability(128));
	public static final DeferredItem<Item> SOAP = ITEMS.registerItem("soap",
			SoapItem::new, new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));
	public static final DeferredItem<Item> SATIPOROJA_SCARF = ITEMS.registerItem("satiporoja_scarf",
			SatiporojaScarfItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final DeferredItem<BreathControlMaskItem> BREATH_CONTROL_MASK = ITEMS.registerItem("breath_control_mask",
			BreathControlMaskItem::new, new Item.Properties().durability(ModArmorMaterials.BREATH_CONTROL_MASK_DURABILITY));
	public static final DeferredItem<BucketItem> BOILING_BLOOD_BUCKET = ITEMS.register("boiling_blood_bucket",
			() -> new BucketItem(ModFluids.BOILING_BLOOD.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
	public static final DeferredItem<Item> STONE_MASK = ITEMS.registerItem("stone_mask",
			props -> new StoneMaskItem(props, ModBlocks.STONE_MASK.get()), new Item.Properties().durability(39).rarity(Rarity.RARE));
	public static final DeferredItem<Item> AJA_STONE_MASK = ITEMS.registerItem("aja_stone_mask",
			props -> new StoneMaskItem(props, ModBlocks.AJA_STONE_MASK.get()), new Item.Properties().durability(39).rarity(Rarity.RARE));
	public static final DeferredItem<BlockItem> SLUMBERING_PILLARMAN = ITEMS.register("slumbering_pillarman",
			() -> new BlockItem(ModBlocks.SLUMBERING_PILLARMAN.get(), new Item.Properties().rarity(Rarity.EPIC)));
	public static final DeferredItem<Item> HUNGRY_ZOMBIE_SPAWN_EGG = ITEMS.register("hungry_zombie_spawn_egg",
			() -> new SpawnEggItem(ModEntityTypes.HUNGRY_ZOMBIE.get(), 0x00AFAF, 0x9B9B9B, new Item.Properties()));
	public static final DeferredItem<Item> HAMON_MASTER_SPAWN_EGG = ITEMS.register("hamon_master_spawn_egg",
			() -> new SpawnEggItem(ModEntityTypes.HAMON_MASTER.get(), 0xF8D100, 0x542722, new Item.Properties()));

	public static final DeferredItem<Item> MANNEQUIN = ITEMS.registerItem("mannequin", props -> new MannequinItem(props, false), new Item.Properties().stacksTo(16));

	public static final DeferredItem<Item> MANNEQUIN_SLIM = ITEMS.registerItem("mannequin_slim", props -> new MannequinItem(props, true), new Item.Properties().stacksTo(16));

	public static final DeferredItem<ClothesItem> CLOTHES_BASE_ITEM = ITEMS.registerItem("clothes", props -> new ClothesItem(props));

	public static final DeferredItem<Item> STAND_ARROW = ITEMS.registerItem("stand_arrow", props -> new StandArrowItem(props), 
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).durability(5));
	public static final DeferredItem<Item> STAND_ARROW_BEETLE = ITEMS.registerItem("stand_arrow_beetle", props -> new StandArrowItem(props), 
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
	public static final DeferredItem<Item> STAND_ARROW_METEORITE = ITEMS.registerItem("stand_arrow_meteorite", props -> new StandArrowItem(props), 
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).durability(25));

	public static final DeferredItem<Item> STAND_ARROW_SHARD = ITEMS.register("stand_arrow_shard", () -> new StandArrowShardItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<BlockItem> METEORIC_IRON = ITEMS.register("meteoric_iron", props -> new BlockItem(ModBlocks.METEORIC_IRON.get(), new Item.Properties()));
	public static final DeferredItem<BlockItem> METEORITE_CORE = ITEMS.register("meteorite_core", props -> new BlockItem(ModBlocks.METEORITE_CORE.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<Item> METEORIC_SCRAP = ITEMS.register("meteoric_scrap", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<Item> METEORIC_INGOT = ITEMS.register("meteoric_ingot", () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
	public static final DeferredItem<Item> GE_BODY_TISSUE = ITEMS.registerItem("gold_experience_body_tissue", props -> new com.github.standobyte.jojoimpl.stands.goldexperience.GEBodyTissueItem(props), new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> CRAZY_DIAMOND_NON_BLOCK_ANCHOR = ITEMS.registerItem("crazy_diamond_non_block_anchor", Item::new, new Item.Properties());
	public static final DeferredItem<Item> MR_PRESIDENT_KEY = ITEMS.registerItem("mr_president_key",
			props -> new MrPresidentKeyItem(props, false), new Item.Properties().stacksTo(1));
	public static final DeferredItem<Item> MR_PRESIDENT_MASTER_KEY = ITEMS.registerItem("mr_president_master_key",
			props -> new MrPresidentKeyItem(props, true), new Item.Properties().stacksTo(1));
	public static final DeferredItem<BlockItem> COCO_JUMBO_SHELL = ITEMS.registerSimpleBlockItem("coco_jumbo_shell",
			ModBlocks.COCO_JUMBO_SHELL, new Item.Properties());
	public static final DeferredItem<BlockItem> MR_PRESIDENT_EXIT = ITEMS.register("mr_president_exit",
			props -> new BlockItem(ModBlocks.MR_PRESIDENT_EXIT.get(), new Item.Properties()));

	public static Comparator<StandInstance> discsOrder(HolderLookup.Provider registries) {
		return Comparator
				.comparingInt((StandInstance stand) -> stand.getStandType().discCategoryPriority)
				.thenComparing((StandInstance stand) -> StoryPart.getStoryPart(stand, registries), StoryPart.COMPARATOR)
				.thenComparingInt((StandInstance stand) -> stand.getStandType().discStoryPartPriority);
	}

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(JojoMod.MOD_ID + "_main", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup." + JojoMod.MOD_ID + "_main"))
			.icon(() -> DEBUG_ITEM.value().getDefaultInstance())
			.displayItems((CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) -> {
				// most of the mod's items
				output.accept(STAND_ARROW.get());
				output.accept(STAND_ARROW_BEETLE.get());
				output.accept(STAND_ARROW_METEORITE.get());
				output.accept(STAND_ARROW_SHARD.get());
				output.accept(METEORIC_IRON.get());
				output.accept(METEORITE_CORE.get());
				output.accept(METEORIC_SCRAP.get());
				output.accept(METEORIC_INGOT.get());
				output.accept(KNIFE.get());
				output.accept(SLEDGEHAMMER.get());
				output.accept(BLADE_HAT.get());
				output.accept(CLACKERS.get());
				output.accept(TommyGunItem.fullAmmoStack());
				output.accept(ROAD_ROLLER.get());
				output.accept(POLAROID.get());
				output.accept(PHOTO.get());
				output.accept(WALKMAN.get());
				output.accept(CASSETTE_BLANK.get());
				output.accept(CASSETTE_RECORDED.get());
				output.accept(OIL.get());
				output.accept(MOLOTOV.get());
				output.accept(AJA_STONE.get());
				output.accept(SUPER_AJA_STONE.get());
				output.accept(GLOVES.get());
				output.accept(BUBBLE_GLOVES.get());
				output.accept(SOAP.get());
				output.accept(SATIPOROJA_SCARF.get());
				output.accept(BREATH_CONTROL_MASK.get());
				output.accept(BOILING_BLOOD_BUCKET.get());
				WOODEN_COFFIN_OAK.values().forEach(item -> output.accept(item.get()));
				output.accept(STONE_MASK.get());
				output.accept(HUNGRY_ZOMBIE_SPAWN_EGG.get());
				output.accept(HAMON_MASTER_SPAWN_EGG.get());

				Stream<StandType> stands = StandType.getAllEnabledStands();
				stands
				.map(StandInstance::new)
				.sorted(discsOrder(parameters.holders()))
				.map(StandDiscItem::withStand)
				.forEach(item -> output.accept(item, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
			}).build());

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void addToModCreativeTabLast(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == MAIN_TAB.getKey()) {
			// items related to clothes
			event.accept(SEWING_MACHINE.get());
			event.accept(SEWING_NEEDLE.get());
			event.accept(MANNEQUIN.get());
			event.accept(MANNEQUIN_SLIM.get());
			for (DyeColor dye : DyeColor.values()) {
				event.accept(CassetteRecordedItem.withDyeRecording(dye), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
			}

			// the clothes items themselves
			ClothesItem clothesFactory = CLOTHES_BASE_ITEM.get();

			event.getParameters().holders()
			.lookup(JojoRegistries.CLOTHES_SETS_REG_KEY)
			.ifPresent(
					clothesSets -> clothesSets.listElements()
					.flatMap(setHolder -> {
						List<ClothesDataComponent> components = new ArrayList<>(ClothesSlotType.values().length);
						for (ClothesSlotType slot : ClothesSlotType.values()) {
							ClothesDataComponent component = ClothesItem.makeItemComponent(setHolder, slot);
							if (component != null) {
								components.add(new ClothesDataComponent(setHolder, slot, ClothesPiece.SubClothingPiece.FULL));
							}
						}
						return components.stream();
					})
					.map(clothesFactory::makeClothesPieceStack)
					.forEach(item -> event.accept(item, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS)));
		}
	}

	private static Map<DyeColor, DeferredItem<BlockItem>> registerWoodenCoffinItems() {
		EnumMap<DyeColor, DeferredItem<BlockItem>> items = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			items.put(color, ITEMS.registerSimpleBlockItem("wooden_coffin_oak_" + color.getName(),
					ModBlocks.WOODEN_COFFIN_OAK.get(color), new Item.Properties()));
		}
		return items;
	}

}

package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import rotp.core.api.trade.ContextualVillagerTrades;
import rotp.core.core.JojoMod;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModItems;
import rotp.core.init.ModStructures;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.TempleMapTradeHandler;
import rotp.core.mechanics.TempleMapTradeHandler.TempleMapTrade;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.worldgen.structure.MeteoriteStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 CustomVillagerTrades.MapTrades and ModStructures.METEORITE: the meteorite
 * structure, the three one-use expert maps with their biome/power chances, the
 * one-time map flavor, and the 2% desert pyramid Beetle Arrow.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExpertMapTradeGameTests {
	private ExpertMapTradeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meteoriteTemplatesCarryCoreAndIron(GameTestHelper helper) {
		var manager = helper.getLevel().getStructureManager();
		Block core = ModBlocks.METEORITE_CORE.get();
		Block iron = ModBlocks.METEORIC_IRON.get();
		for (ResourceLocation body : MeteoriteStructure.BODIES) {
			StructureTemplate template = manager.get(body).orElse(null);
			helper.assertTrue(template != null, "meteorite body template missing: " + body);
			helper.assertTrue(!template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), core).isEmpty(),
					body + " has no meteorite core (1.16 jojo:meteoric_ore)");
			helper.assertTrue(!template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), iron).isEmpty(),
					body + " has no meteoric iron");
		}
		List<ResourceLocation> shells = new ArrayList<>(List.of(MeteoriteStructure.CRATERS));
		shells.add(MeteoriteStructure.TRAIL);
		for (ResourceLocation shell : shells) {
			StructureTemplate template = manager.get(shell).orElse(null);
			helper.assertTrue(template != null && template.getSize().getY() > 0, "meteorite template missing: " + shell);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meteoriteGeneratesInSnowyBiomes(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		ResourceLocation id = JojoMod.resLoc("meteorite");
		Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
		Structure structure = structures.get(id);
		helper.assertTrue(structure != null && structure.type() == ModStructures.METEORITE.get(),
				"jojo_ripples:meteorite structure is not registered");
		helper.assertTrue(structure.step() == GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				"1.16 meteorite step is TOP_LAYER_MODIFICATION, got " + structure.step());
		helper.assertTrue(structures.getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, id))
				.is(TempleMapTradeHandler.METEORITE_MAPS), "the meteorite is not in #on_meteorite_maps");

		Registry<Biome> biomes = access.registryOrThrow(Registries.BIOME);
		for (ResourceKey<Biome> snowy : List.of(Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.SNOWY_TAIGA, Biomes.FROZEN_RIVER)) {
			helper.assertTrue(structure.biomes().contains(biomes.getHolderOrThrow(snowy)),
					"meteorite cannot generate in snowy biome " + snowy.location());
		}
		for (ResourceKey<Biome> other : List.of(Biomes.PLAINS, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.TAIGA)) {
			helper.assertTrue(!structure.biomes().contains(biomes.getHolderOrThrow(other)),
					"meteorite may generate in non-snowy or ocean biome " + other.location());
		}

		StructureSet set = access.registryOrThrow(Registries.STRUCTURE_SET).get(id);
		helper.assertTrue(set != null, "jojo_ripples:meteorite structure set is missing");
		JsonObject placement = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, set.placement())
				.getOrThrow().getAsJsonObject();
		helper.assertTrue(placement.get("spacing").getAsInt() == 40 && placement.get("separation").getAsInt() == 12
				&& placement.get("salt").getAsInt() == 286704381,
				"1.16 meteorite spacing 40 / separation 12 / salt 286704381, got " + placement);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void desertPyramidSometimesHoldsBeetleArrow(GameTestHelper helper) {
		LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(BuiltInLootTables.DESERT_PYRAMID);
		LootParams params = new LootParams.Builder(helper.getLevel())
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
				.create(LootContextParamSets.CHEST);
		int rolls = 1000;
		int arrows = 0;
		for (int i = 0; i < rolls; i++) {
			for (ItemStack stack : table.getRandomItems(params)) {
				if (stack.is(ModItems.STAND_ARROW_BEETLE.get())) {
					arrows++;
				}
			}
		}
		// 1.16: 2% per chest; P(0 in 1000) ~ 2e-9, P(>=80) negligible
		helper.assertTrue(arrows > 0 && arrows < 80,
				"desert pyramid Beetle Arrow count " + arrows + " in " + rolls + " rolls (expected ~20)");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void expertMapChancesMatch116(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the map trade test player");

			// no power: the 1.16 table in CustomVillagerTrades.MapTrades
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.TAIGA), 1, "hamon taiga");
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.SNOW), 0, "hamon snow");
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.JUNGLE), 0, "hamon jungle");
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.PLAINS), 0.5, "hamon plains");
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.SWAMP), 0.5, "hamon swamp");
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.DESERT), 0.125, "hamon desert");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.SNOW), 1, "meteorite snow");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.TAIGA), 0, "meteorite taiga");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.SWAMP), 0.8, "meteorite swamp");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.PLAINS), 0.2, "meteorite plains");
			expect(helper, TempleMapTradeHandler.pillarmanMapChance(player, VillagerType.JUNGLE), 1, "pillar man jungle");
			expect(helper, TempleMapTradeHandler.pillarmanMapChance(player, VillagerType.DESERT), 0.25, "pillar man desert");

			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.TAIGA), 0.25, "hamon taiga, Hamon user");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.SNOW), 0.0625, "meteorite snow, Hamon user");
			expect(helper, TempleMapTradeHandler.pillarmanMapChance(player, VillagerType.JUNGLE), 0.4, "pillar man jungle, Hamon user");

			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			expect(helper, TempleMapTradeHandler.hamonMapChance(player, VillagerType.TAIGA), 0.125, "hamon taiga, vampire");
			expect(helper, TempleMapTradeHandler.meteoriteMapChance(player, VillagerType.SNOW), 0.05, "meteorite snow, vampire");
			expect(helper, TempleMapTradeHandler.pillarmanMapChance(player, VillagerType.JUNGLE), 0, "pillar man jungle, vampire");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void expertMapsAreOneUseUniqueTrades(GameTestHelper helper) {
		expectTrade(helper, TempleMapTradeHandler.METEORITE_MAP_TRADE, 16, 15);
		expectTrade(helper, TempleMapTradeHandler.HAMON_MAP_TRADE, 24, 23);
		expectTrade(helper, TempleMapTradeHandler.PILLARMAN_MAP_TRADE, 32, 30);

		List<ResourceLocation> owners = ContextualVillagerTrades.registeredOwners();
		for (ResourceLocation owner : List.of(TempleMapTradeHandler.METEORITE_MAP_PROVIDER,
				TempleMapTradeHandler.HAMON_MAP_PROVIDER, TempleMapTradeHandler.PILLARMAN_MAP_PROVIDER)) {
			helper.assertTrue(owners.contains(owner), "expert map provider not registered: " + owner);
		}

		// the Hamon map is no longer a plain level-4 cartographer listing
		Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
		for (int level = 1; level <= 5; level++) {
			trades.put(level, new ArrayList<>());
		}
		NeoForge.EVENT_BUS.post(new VillagerTradesEvent(trades, VillagerProfession.CARTOGRAPHER, helper.getLevel().registryAccess()));
		for (List<VillagerTrades.ItemListing> listings : trades.values()) {
			for (VillagerTrades.ItemListing listing : listings) {
				helper.assertTrue(!(listing instanceof TempleMapTrade),
						"a cartographer level listing still holds an expert map: " + listing);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void mapFlavorOncePerPlayerAndKind(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			helper.assertTrue(TempleMapTradeHandler.claimFirstBuyFlavor(player, TempleMapTradeHandler.HAMON_TEMPLE),
					"the first Hamon map bought gave no flavor");
			helper.assertTrue(!TempleMapTradeHandler.claimFirstBuyFlavor(player, TempleMapTradeHandler.HAMON_TEMPLE),
					"the second Hamon map bought gave the flavor again (1.16 OneTimeNotification)");
			helper.assertTrue(TempleMapTradeHandler.claimFirstBuyFlavor(player, TempleMapTradeHandler.METEORITE),
					"the first meteorite map lost its flavor after a Hamon map");
			helper.assertTrue(!player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).isEmpty(),
					"the flavor flags are not kept under PlayerPersisted, so they reset on death");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	private static void expectTrade(GameTestHelper helper, TempleMapTrade trade, int emeralds, int xp) {
		helper.assertTrue(trade.maxUses() == 1 && trade.emeraldCost() == emeralds && trade.villagerXp() == xp,
				trade.structureName() + " map: 1.16 is " + emeralds + " emeralds, 1 use, " + xp + " xp; got "
						+ trade.emeraldCost() + ", " + trade.maxUses() + ", " + trade.villagerXp());
	}

	private static void expect(GameTestHelper helper, double actual, double expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 1.0E-9, what + ": expected " + expected + ", got " + actual);
	}
}

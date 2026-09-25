package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 loot modifiers add Aja Stone (Fortune 0-III: 2-5%) and Super Aja Stone (0.02-0.05%)
 * to non-silk-touch redstone ore drops; deepslate redstone ore must drop them too.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RedstoneOreAjaDropGameTests {
	private RedstoneOreAjaDropGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void ajaStonesDropFromEveryRedstoneOre(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		ItemStack silkPick = enchantedPick(level, Enchantments.SILK_TOUCH, 1);
		ItemStack fortunePick = enchantedPick(level, Enchantments.FORTUNE, 3);
		ItemStack efficiencyPick = enchantedPick(level, Enchantments.EFFICIENCY, 5);
		Item aja = ModItems.AJA_STONE.get();
		Item superAja = ModItems.SUPER_AJA_STONE.get();
		for (Block ore : List.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE)) {
			String name = BuiltInRegistries.BLOCK.getKey(ore).toString();
			// every chance roll wins
			List<ItemStack> lucky = roll(helper, ore, pick, 0F);
			helper.assertTrue(has(lucky, Items.REDSTONE), name + " vanilla drops missing: " + lucky);
			helper.assertTrue(has(lucky, aja) && has(lucky, superAja),
					name + " gave no Aja stones on a winning roll: " + lucky);
			// silk touch: no Aja stones
			List<ItemStack> silk = roll(helper, ore, silkPick, 0F);
			helper.assertTrue(!has(silk, aja) && !has(silk, superAja),
					name + " gave Aja stones with silk touch: " + silk);
			// only silk touch blocks them, not any enchantment
			List<ItemStack> efficiency = roll(helper, ore, efficiencyPick, 0F);
			helper.assertTrue(has(efficiency, aja) && has(efficiency, superAja),
					name + " treated a non-silk enchanted pick as silk touch: " + efficiency);
			// roll 0.03: above the 2% base chance, below the 5% Fortune III chance
			List<ItemStack> plain = roll(helper, ore, pick, 0.03F);
			helper.assertTrue(!has(plain, aja) && !has(plain, superAja),
					name + " ignored the 2% base chance: " + plain);
			List<ItemStack> fortune = roll(helper, ore, fortunePick, 0.03F);
			helper.assertTrue(has(fortune, aja) && !has(fortune, superAja),
					name + " ignored the Fortune chances: " + fortune);
			// roll 0.0004: above the 0.02% Super Aja base chance, below its 0.05% Fortune III chance
			List<ItemStack> rarePlain = roll(helper, ore, pick, 0.0004F);
			helper.assertTrue(has(rarePlain, aja) && !has(rarePlain, superAja),
					name + " ignored the 0.02% Super Aja base chance: " + rarePlain);
			List<ItemStack> rareFortune = roll(helper, ore, fortunePick, 0.0004F);
			helper.assertTrue(has(rareFortune, aja) && has(rareFortune, superAja),
					name + " ignored the Super Aja Fortune chances: " + rareFortune);
		}
		helper.succeed();
	}

	// 1.16 meteoric_ore.json: only silk touch drops the Meteorite Core, any other pick gives Meteoric Scrap
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void meteoriteCoreNeedsSilkTouch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Block core = ModBlocks.METEORITE_CORE.get();
		Item coreItem = ModItems.METEORITE_CORE.get();
		Item scrap = ModItems.METEORIC_SCRAP.get();
		List<ItemStack> nonSilkPicks = List.of(new ItemStack(Items.IRON_PICKAXE),
				enchantedPick(level, Enchantments.EFFICIENCY, 5),
				enchantedPick(level, Enchantments.FORTUNE, 3),
				enchantedPick(level, Enchantments.UNBREAKING, 3));
		for (ItemStack pick : nonSilkPicks) {
			List<ItemStack> drops = roll(helper, core, pick, 0F);
			helper.assertTrue(has(drops, scrap) && !has(drops, coreItem),
					"Meteorite Core with " + pick.getTagEnchantments() + " should drop only scrap: " + drops);
		}
		List<ItemStack> silk = roll(helper, core, enchantedPick(level, Enchantments.SILK_TOUCH, 1), 0F);
		helper.assertTrue(has(silk, coreItem) && !has(silk, scrap),
				"Meteorite Core with silk touch should drop itself: " + silk);
		helper.succeed();
	}

	// full block loot path, so the global loot modifiers run
	private static List<ItemStack> roll(GameTestHelper helper, Block ore, ItemStack tool, float chanceRoll) {
		ServerLevel level = helper.getLevel();
		LootTable table = level.getServer().reloadableRegistries().getLootTable(ore.getLootTable());
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)))
				.withParameter(LootContextParams.TOOL, tool)
				.withParameter(LootContextParams.BLOCK_STATE, ore.defaultBlockState())
				.create(LootContextParamSets.BLOCK);
		return table.getRandomItems(params, new FixedFloatRandom(chanceRoll));
	}

	private static ItemStack enchantedPick(ServerLevel level, ResourceKey<Enchantment> key, int lvl) {
		ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
		stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key), lvl);
		return stack;
	}

	private static boolean has(List<ItemStack> drops, Item item) {
		return drops.stream().anyMatch(stack -> stack.is(item));
	}

	// fixed nextFloat makes chance conditions deterministic; int rolls stay seeded
	private static final class FixedFloatRandom extends LegacyRandomSource {
		private final float value;

		FixedFloatRandom(float value) {
			super(0L);
			this.value = value;
		}

		@Override
		public float nextFloat() {
			return value;
		}
	}
}

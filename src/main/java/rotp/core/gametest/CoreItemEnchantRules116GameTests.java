package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEnchantments;
import rotp.core.init.ModItems;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 decided per item at the enchanting table by canApplyAtEnchantingTable (the enchantment's EnchantmentType, plus
 * the item's override) and its enchantability, and at the anvil by Enchantment.canEnchant, which Forge made return
 * canApplyAtEnchantingTable, so the anvil took the table's set (treasure included) plus what the enchantment classes'
 * own canEnchant added: Thorns for any ArmorItem, Unbreaking for a damageable item, Efficiency for shears, the damage
 * enchantments for an axe. 1.21 asks ItemStack.isPrimaryItemFor and getEnchantmentValue at the table and
 * supportsEnchantment at the anvil. Head armor (ArmorItem, HEAD) took ARMOR, ARMOR_HEAD, WEARABLE and VANISHABLE, plus
 * BREAKABLE with durability.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreItemEnchantRules116GameTests {
	private static final String BATCH = "core_item_enchant_rules_116";
	private static final List<String> HEAD_ARMOR = List.of("minecraft:protection", "minecraft:fire_protection",
			"minecraft:blast_protection", "minecraft:projectile_protection", "minecraft:respiration", "minecraft:aqua_affinity",
			"minecraft:binding_curse", "minecraft:vanishing_curse");
	private static final List<String> BREAKABLE = List.of("minecraft:unbreaking", "minecraft:mending");
	private static final List<String> THORNS = List.of("minecraft:thorns");
	private static final List<String> SLEDGEHAMMER_TOOL = List.of("minecraft:efficiency", "minecraft:silk_touch",
			"minecraft:fortune", "minecraft:unbreaking", "minecraft:mending", "minecraft:vanishing_curse");
	private static final List<String> NONE = List.of();
	private static final List<String> SLEDGEHAMMER_WEAPON = List.of("minecraft:sharpness", "minecraft:smite",
			"minecraft:bane_of_arthropods", "minecraft:knockback", "minecraft:fire_aspect", "minecraft:looting");
	private static final List<String> GLOVES = List.of("jojo_ripples:gloves_damage", "jojo_ripples:gloves_speed");
	private static final List<String> STAND_ARROW = List.of("minecraft:unbreaking", "minecraft:mending",
			"minecraft:vanishing_curse", "jojo_ripples:virus_inhibition", "jojo_ripples:stand_arrow_xp_reduction");

	private CoreItemEnchantRules116GameTests() {}

	// anvilOnly: what the enchantment classes' own canEnchant added to canApplyAtEnchantingTable
	private record Rule(Item item, boolean tableEnchantable, int enchantability, Set<String> table, List<String> anvilOnly) {
		Set<String> anvil() {
			return set(List.copyOf(table), anvilOnly);
		}
	}

	private static Set<String> set(List<?>... parts) {
		Set<String> set = new TreeSet<>();
		for (List<?> part : parts) {
			part.forEach(name -> set.add((String) name));
		}
		return set;
	}

	private static List<Rule> rules() {
		Set<String> mask = set(HEAD_ARMOR, BREAKABLE);
		Set<String> arrow = set(STAND_ARROW, List.of("minecraft:loyalty", "minecraft:sharpness"));
		// Unbreaking is already in the table's set of every damageable item here (BREAKABLE)
		return List.of(
				// PickaxeItem, ItemTier.IRON (14), plus the weapon set but Sweeping (SledgehammerItem.canApplyAtEnchantingTable)
				new Rule(ModItems.SLEDGEHAMMER.get(), true, 14, set(SLEDGEHAMMER_TOOL, SLEDGEHAMMER_WEAPON), NONE),
				// ModArmorMaterials.STONE_MASK: enchantability 0, durability 33
				new Rule(ModItems.STONE_MASK.get(), true, 0, mask, THORNS),
				new Rule(ModItems.AJA_STONE_MASK.get(), true, 0, mask, THORNS),
				// BLACK_CLOTH (15); BladeHatItem.canApplyAtEnchantingTable adds Sharpness
				new Rule(ModItems.BLADE_HAT.get(), true, 15, set(HEAD_ARMOR, BREAKABLE, List.of("minecraft:sharpness")), THORNS),
				new Rule(ModItems.BREATH_CONTROL_MASK.get(), true, 9, mask, THORNS),
				// SATIPOROJA_SCARF: durability 0, so not BREAKABLE and not enchantable at the table
				new Rule(ModItems.SATIPOROJA_SCARF.get(), false, 25, set(HEAD_ARMOR), THORNS),
				// GlovesItem: the JOJO_GLOVES type, enchantability 15, Knockback (GlovesItem.canApplyAtEnchantingTable)
				new Rule(ModItems.GLOVES.get(), true, 15, set(GLOVES, List.of("minecraft:knockback")), NONE),
				new Rule(ModItems.BUBBLE_GLOVES.get(), true, 15, set(GLOVES, List.of("minecraft:knockback")), NONE),
				// durability 640, Item's enchantability 0
				new Rule(ModItems.SUPER_AJA_STONE.get(), true, 0, set(BREAKABLE, List.of("minecraft:vanishing_curse")), NONE),
				// the JOJO_STAND_ARROW type and BREAKABLE; Loyalty and Sharpness (StandArrowItem.canApplyAtEnchantingTable)
				new Rule(ModItems.STAND_ARROW.get(), true, 10, arrow, NONE),
				new Rule(ModItems.STAND_ARROW_BEETLE.get(), true, 25, arrow, NONE),
				new Rule(ModItems.STAND_ARROW_METEORITE.get(), true, 10, arrow, NONE));
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void coreItemsTakeTheEnchantments116TookAtTheTableAndTheAnvil(GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		List<String> wrong = new ArrayList<>();
		for (Rule rule : rules()) {
			ItemStack stack = new ItemStack(rule.item());
			Set<String> anvil = new TreeSet<>();
			Set<String> table = new TreeSet<>();
			registry.holders().forEach(holder -> {
				String name = holder.key().location().toString();
				if (!name.startsWith("minecraft:") && !name.startsWith(JojoMod.MOD_ID + ":")) {
					return;
				}
				if (stack.supportsEnchantment(holder)) {
					anvil.add(name);
				}
				if (stack.isPrimaryItemFor(holder)) {
					table.add(name);
				}
			});
			String id = rule.item().builtInRegistryHolder().key().location().toString();
			if (!anvil.equals(rule.anvil())) {
				wrong.add(id + " anvil (supportsEnchantment) " + anvil + ", 1.16 " + rule.anvil());
			}
			if (!table.equals(rule.table())) {
				wrong.add(id + " table (isPrimaryItemFor) " + table + ", 1.16 " + rule.table());
			}
			if (stack.isEnchantable() != rule.tableEnchantable() || stack.getEnchantmentValue() != rule.enchantability()) {
				wrong.add(id + " enchantable " + stack.isEnchantable() + " with enchantability " + stack.getEnchantmentValue()
						+ ", 1.16 " + rule.tableEnchantable() + " with " + rule.enchantability());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Core items take other enchantments than in 1.16: " + String.join("; ", wrong));
		helper.succeed();
	}

	// EnchantmentHelper.selectEnchantment as the table rolls it, at level 30 for seeds 0 to 199
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theTableOffersTheSledgehammer116sSetAndAStoneMaskNothing(GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Set<String> sledgehammer = offers(registry, new ItemStack(ModItems.SLEDGEHAMMER.get()));
		Set<String> expected = set(List.of("minecraft:efficiency", "minecraft:silk_touch", "minecraft:fortune",
				"minecraft:unbreaking"), SLEDGEHAMMER_WEAPON);
		helper.assertTrue(sledgehammer.equals(expected),
				"1.16's table offered the sledgehammer its pickaxe and weapon enchantments but Sweeping and the treasure ones"
						+ " (seeds 0 to 199, level 30): offered " + sledgehammer + ", expected " + expected);
		for (Item item : List.of(ModItems.STONE_MASK.get(), ModItems.AJA_STONE_MASK.get())) {
			ItemStack mask = new ItemStack(item);
			Set<String> offered = offers(registry, mask);
			int cost = 0;
			for (int slot = 0; slot < 3; slot++) {
				cost = Math.max(cost, EnchantmentHelper.getEnchantmentCost(RandomSource.create(slot), slot, 15, mask));
			}
			helper.assertTrue(offered.isEmpty() && cost == 0,
					"1.16's table offered a stone mask nothing (enchantability 0): " + item + " offered " + offered
							+ ", highest cost " + cost);
		}
		Set<String> bladeHat = offers(registry, new ItemStack(ModItems.BLADE_HAT.get()));
		helper.assertTrue(bladeHat.contains("minecraft:sharpness") && !bladeHat.contains("minecraft:thorns")
				&& !bladeHat.contains("minecraft:mending"),
				"1.16's table offered the blade hat Sharpness and no Thorns or treasure: offered " + bladeHat);
		helper.succeed();
	}

	private static Set<String> offers(Registry<Enchantment> registry, ItemStack stack) {
		Set<String> offered = new TreeSet<>();
		HolderSet<Enchantment> pool = registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
		for (int seed = 0; seed < 200; seed++) {
			for (EnchantmentInstance offer : EnchantmentHelper.selectEnchantment(RandomSource.create(seed), stack, 30, pool.stream())) {
				offered.add(offer.enchantment.getRegisteredName());
			}
		}
		return offered;
	}

	// the anvil with a book: applied or no result (1.16 RepairContainer asked enchantment.canEnchant(left))
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void theAnvilTakesTheBooks116sCanEnchantTookOnCoreItems(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		record Row(Item item, ResourceKey<Enchantment> book, boolean applies) {}
		List<Row> rows = List.of(
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.EFFICIENCY, true),
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.FORTUNE, true),
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.MENDING, true),
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.SHARPNESS, true),
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.LOOTING, true),
				new Row(ModItems.SLEDGEHAMMER.get(), Enchantments.SWEEPING_EDGE, false),
				new Row(ModItems.STONE_MASK.get(), Enchantments.PROTECTION, true),
				new Row(ModItems.STONE_MASK.get(), Enchantments.THORNS, true),
				new Row(ModItems.STONE_MASK.get(), Enchantments.MENDING, true),
				new Row(ModItems.AJA_STONE_MASK.get(), Enchantments.RESPIRATION, true),
				new Row(ModItems.BLADE_HAT.get(), Enchantments.SHARPNESS, true),
				new Row(ModItems.BLADE_HAT.get(), Enchantments.THORNS, true),
				new Row(ModItems.BLADE_HAT.get(), Enchantments.SMITE, false),
				new Row(ModItems.SATIPOROJA_SCARF.get(), Enchantments.MENDING, false),
				new Row(ModItems.SATIPOROJA_SCARF.get(), Enchantments.PROTECTION, true),
				new Row(ModItems.GLOVES.get(), Enchantments.KNOCKBACK, true),
				new Row(ModItems.BUBBLE_GLOVES.get(), Enchantments.KNOCKBACK, true),
				new Row(ModItems.GLOVES.get(), Enchantments.SHARPNESS, false),
				new Row(ModItems.STAND_ARROW.get(), Enchantments.LOYALTY, true),
				new Row(ModItems.STAND_ARROW_BEETLE.get(), Enchantments.SHARPNESS, true),
				new Row(ModItems.STAND_ARROW.get(), Enchantments.POWER, false),
				new Row(ModItems.SUPER_AJA_STONE.get(), Enchantments.UNBREAKING, true));
		List<String> wrong = new ArrayList<>();
		for (Row row : rows) {
			Holder<Enchantment> enchantment = registry.getHolderOrThrow(row.book());
			AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
			menu.getSlot(0).set(new ItemStack(row.item()));
			menu.getSlot(1).set(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1)));
			menu.createResult();
			ItemStack result = menu.getSlot(2).getItem();
			boolean applied = result.is(row.item()) && EnchantmentHelper.getItemEnchantmentLevel(enchantment, result) == 1;
			if (applied != row.applies() || !row.applies() && !result.isEmpty()) {
				wrong.add(row.item() + " + " + row.book().location() + " book: " + result + ", 1.16 "
						+ (row.applies() ? "applied" : "no result"));
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The anvil takes other books than 1.16's Enchantment.canEnchant: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	// 1.16 GlovesItem.combineInAnvil merged the right pair's enchantments that enchantment.canEnchant(left) took
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void twoGlovesCombinedOnTheAnvilKeepKnockbackAsIn116(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> knockback = registry.getHolderOrThrow(Enchantments.KNOCKBACK);
		Holder<Enchantment> glovesDamage = registry.getHolderOrThrow(ModEnchantments.GLOVES_DAMAGE);
		for (Item item : List.of(ModItems.GLOVES.get(), ModItems.BUBBLE_GLOVES.get())) {
			ItemStack right = new ItemStack(item);
			right.enchant(knockback, 1);
			right.enchant(glovesDamage, 1);
			AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
			menu.getSlot(0).set(new ItemStack(item));
			menu.getSlot(1).set(right);
			menu.createResult();
			ItemStack result = menu.getSlot(2).getItem();
			helper.assertTrue(result.is(item) && EnchantmentHelper.getItemEnchantmentLevel(knockback, result) == 1
					&& EnchantmentHelper.getItemEnchantmentLevel(glovesDamage, result) == 1,
					"1.16: two " + item + " combined on the anvil keep Knockback I and gloves damage I; got " + result
							+ " " + EnchantmentHelper.getEnchantmentsForCrafting(result));
		}
		helper.succeed();
	}
}

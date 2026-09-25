package rotp.core.gametest;

import java.util.List;
import java.util.stream.Stream;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowXpReductionEnchantment: isAllowedOnBooks() false, so the enchanting table offered Spiritual Strength
 * on Stand Arrows only, never on a plain Book.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnchantingTableBookFilterGameTests {

	private static final ResourceKey<Enchantment> SPIRITUAL_STRENGTH = ResourceKey.create(
			Registries.ENCHANTMENT, JojoMod.resLoc("stand_arrow_xp_reduction"));

	private EnchantingTableBookFilterGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void spiritualStrengthIsOfferedForStandArrowsButNotBooks(GameTestHelper helper) {
		Registry<Enchantment> registry = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		Holder<Enchantment> reduction = registry.getHolderOrThrow(SPIRITUAL_STRENGTH);
		helper.assertTrue(reduction.is(EnchantmentTags.IN_ENCHANTING_TABLE),
				"Spiritual Strength must stay a table enchantment for Stand Arrows");
		helper.assertTrue(reduction.is(ModEnchantments.NOT_ALLOWED_ON_BOOKS),
				"Spiritual Strength must be in #jojo_ripples:not_allowed_on_books");

		ItemStack book = new ItemStack(Items.BOOK);
		for (int level = 1; level <= 40; level++) {
			List<EnchantmentInstance> offers = tableResults(registry, level, book);
			int found = levelOf(offers, SPIRITUAL_STRENGTH);
			helper.assertTrue(found == 0,
					"A plain Book at table level " + level + " was offered Spiritual Strength " + found);
		}
		// vanilla Book rolls stay: Sharpness III at 30
		helper.assertTrue(levelOf(tableResults(registry, 30, book), Enchantments.SHARPNESS) == 3,
				"A plain Book at level 30 must still be offered Sharpness III, got "
				+ levelOf(tableResults(registry, 30, book), Enchantments.SHARPNESS));

		// 1 + level * 11 (min) .. + 10 (max): 34 is Spiritual Strength III on a Stand Arrow
		ItemStack arrow = new ItemStack(ModItems.STAND_ARROW.get());
		int arrowLevel = levelOf(tableResults(registry, 34, arrow), SPIRITUAL_STRENGTH);
		helper.assertTrue(arrowLevel == 3,
				"A Stand Arrow at table level 34 must be offered Spiritual Strength III, got " + arrowLevel);
		helper.succeed();
	}

	private static List<EnchantmentInstance> tableResults(Registry<Enchantment> registry, int level, ItemStack stack) {
		Stream<Holder<Enchantment>> table = registry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE)
				.map(HolderSet::stream).orElseGet(Stream::empty);
		return EnchantmentHelper.getAvailableEnchantmentResults(level, stack, table);
	}

	private static int levelOf(List<EnchantmentInstance> offers, ResourceKey<Enchantment> key) {
		for (EnchantmentInstance offer : offers) {
			if (offer.enchantment.is(key)) {
				return offer.level;
			}
		}
		return 0;
	}
}

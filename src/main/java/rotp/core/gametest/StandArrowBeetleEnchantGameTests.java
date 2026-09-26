package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandVirusActualEffect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The donor Beetle Arrow has configurable durability and remains table-enchantable.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowBeetleEnchantGameTests {
	private static final ResourceKey<Enchantment> XP_REDUCTION = ResourceKey.create(Registries.ENCHANTMENT,
			ResourceLocation.fromNamespaceAndPath(JojoMod.MOD_ID, "stand_arrow_xp_reduction"));

	private StandArrowBeetleEnchantGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void enchantingTableOffersEnchantmentsForBeetleArrow(GameTestHelper helper) {
		ItemStack arrow = new ItemStack(ModItems.STAND_ARROW_BEETLE.get());
		helper.assertTrue(arrow.isDamageableItem() && arrow.getMaxDamage() > 0,
				"The beetle arrow must retain its configured durability");
		helper.assertTrue(arrow.isEnchantable(), "1.16: the beetle arrow must be enchantable at the table");

		// real table menu: slotsChanged only fills offers when the stack is enchantable
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		BlockPos tablePos = helper.absolutePos(BlockPos.ZERO);
		EnchantmentMenu menu = new EnchantmentMenu(0, player.getInventory(),
				ContainerLevelAccess.create(helper.getLevel(), tablePos));
		menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
		menu.getSlot(0).set(arrow.copy());

		IdMap<Holder<Enchantment>> ids = helper.getLevel().registryAccess()
				.registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
		List<String> offers = new ArrayList<>();
		for (int slot = 0; slot < 3; slot++) {
			if (menu.costs[slot] <= 0 || menu.enchantClue[slot] < 0) {
				continue;
			}
			Holder<Enchantment> clue = ids.byId(menu.enchantClue[slot]);
			helper.assertTrue(clue != null, "Offer " + slot + " has an unknown enchantment id " + menu.enchantClue[slot]);
			offers.add(clue.getRegisteredName());
			// 1.16 VirusInhibitionEnchantment is in the STAND_ARROW category (any StandArrowItem, the beetle arrow too)
			helper.assertTrue(clue.is(Enchantments.LOYALTY) || clue.is(Enchantments.SHARPNESS) || clue.is(XP_REDUCTION)
							|| clue.is(StandVirusActualEffect.VIRUS_INHIBITION),
					"The table may only offer Loyalty, Sharpness, Spiritual Strength or Virus Inhibition for the beetle arrow; got "
							+ clue.getRegisteredName());
		}
		helper.assertTrue(!offers.isEmpty(), "1.16: the enchanting table offers enchantments for the beetle arrow; costs "
				+ menu.costs[0] + "/" + menu.costs[1] + "/" + menu.costs[2] + ", offers " + offers);
		helper.succeed();
	}
}

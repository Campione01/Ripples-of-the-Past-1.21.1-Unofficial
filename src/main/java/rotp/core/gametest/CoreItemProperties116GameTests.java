package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's item properties of the core items: ModArmorMaterials.STONE_MASK (durability factor 3, so 33 on the head,
 * Ingredient.EMPTY to repair with, SoundEvents.ARMOR_EQUIP_GENERIC), the gloves' Item.Properties (stacksTo(1) only, no
 * durability) and the Stand Arrows' rarities in ModItems (UNCOMMON, the Beetle Arrow RARE).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreItemProperties116GameTests {
	private static final String BATCH = "core_item_properties_116";

	private CoreItemProperties116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void stoneMasksHave116sDurabilityNoLeatherRepairAndTheGenericEquipSound(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (Item item : List.of(ModItems.STONE_MASK.get(), ModItems.AJA_STONE_MASK.get())) {
			ItemStack mask = new ItemStack(item);
			helper.assertTrue(mask.getMaxDamage() == 33, "1.16: " + item + " has durability 33; got " + mask.getMaxDamage());
			helper.assertTrue(((ArmorItem) item).getEquipSound().value() == SoundEvents.ARMOR_EQUIP_GENERIC.value(),
					"1.16: " + item + " equips with the generic armor sound; got " + ((ArmorItem) item).getEquipSound().value().getLocation());
			mask.setDamageValue(20);
			AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.NULL);
			menu.getSlot(0).set(mask);
			menu.getSlot(1).set(new ItemStack(Items.LEATHER, 4));
			menu.createResult();
			ItemStack result = menu.getSlot(2).getItem();
			helper.assertTrue(result.isEmpty() && !item.isValidRepairItem(mask, new ItemStack(Items.LEATHER)),
					"1.16: leather does not repair " + item + " on an anvil (Ingredient.EMPTY); got " + result
							+ " at damage " + result.getDamageValue());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void glovesHaveNoDurabilityAsIn116(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (Item item : List.of(ModItems.GLOVES.get(), ModItems.BUBBLE_GLOVES.get())) {
			ItemStack gloves = new ItemStack(item);
			helper.assertTrue(!gloves.isDamageableItem() && !item.components().has(DataComponents.MAX_DAMAGE)
					&& gloves.getMaxDamage() == 0 && gloves.getMaxStackSize() == 1,
					"1.16: " + item + " is unstackable and has no durability; max damage " + gloves.getMaxDamage());
			// the wear an Angelo rock broken by the held item takes (AngeloRockEntity: hurtAndBreak(1, MAINHAND))
			player.setItemInHand(InteractionHand.MAIN_HAND, gloves);
			gloves.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			helper.assertTrue(player.getMainHandItem() == gloves && gloves.getDamageValue() == 0,
					"1.16: " + item + " held in the hand takes no wear; got " + player.getMainHandItem() + " at damage "
							+ gloves.getDamageValue());
			// two worn pairs made one in the crafting grid when they had durability
			ItemStack worn = new ItemStack(item);
			worn.setDamageValue(10);
			CraftingInput input = CraftingInput.of(2, 1, List.of(worn, worn.copy()));
			helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
					"1.16: two " + item + " do not combine in the crafting grid");
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void standArrowsHave116sRarities(GameTestHelper helper) {
		Rarity arrow = new ItemStack(ModItems.STAND_ARROW.get()).getRarity();
		Rarity beetle = new ItemStack(ModItems.STAND_ARROW_BEETLE.get()).getRarity();
		Rarity meteorite = new ItemStack(ModItems.STAND_ARROW_METEORITE.get()).getRarity();
		helper.assertTrue(arrow == Rarity.UNCOMMON && beetle == Rarity.RARE && meteorite == Rarity.UNCOMMON,
				"1.16: the Stand Arrow is UNCOMMON and the Beetle Arrow RARE, the meteorite arrow as the Stand Arrow; got "
						+ arrow + ", " + beetle + ", " + meteorite);
		helper.succeed();
	}
}

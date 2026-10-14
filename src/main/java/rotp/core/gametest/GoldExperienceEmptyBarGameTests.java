package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModItems;
import rotp.core.powersystem.ability.controls.InputMethod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GoldExperienceHealingItem (staminaCost 40) checked only the off-hand material: on any bar the Stand task spent
 * the material, gave the body tissue and then paid, ignoring a failed payment (StandEntityTask), so a short bar dropped
 * to 0 and the material was never kept back for want of stamina.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceEmptyBarGameTests {
	private GoldExperienceEmptyBarGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void healingItemTurnsMaterialIntoTissueOnAnEmptyBar(GameTestHelper helper) {
		EmptyStaminaBarScene scene = new EmptyStaminaBarScene(helper);
		List<Float> staminaWhenSpent = new ArrayList<>();
		try {
			scene.start("gold_experience", -90, 3, "heal", "heal_other");
			scene.user.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COBBLESTONE));
			scene.afterStandTick(() -> {
				if (scene.user.getOffhandItem().isEmpty() && staminaWhenSpent.isEmpty()) {
					staminaWhenSpent.add(scene.power.getStamina());
				}
			});
			helper.assertTrue(scene.pressOnBar("healing_item", InputMethod.CLICK, 0),
					"1.16 checked only the material: the healing item must be admitted on an empty bar");
			helper.runAfterDelay(25, () -> {
				try {
					helper.assertTrue(scene.user.getOffhandItem().isEmpty(),
							"The material must be spent on an empty bar: " + scene.user.getOffhandItem());
					helper.assertTrue(holdsTissue(scene), "The body tissue must be made on an empty bar");
					helper.assertTrue(staminaWhenSpent.size() == 1 && staminaWhenSpent.get(0) == 0,
							"The 40 cost paid on an empty bar keeps it at 0: " + staminaWhenSpent);
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			scene.close();
			throw failure;
		}
	}

	private static boolean holdsTissue(EmptyStaminaBarScene scene) {
		Inventory inventory = scene.user.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(ModItems.GE_BODY_TISSUE.get())) {
				return true;
			}
		}
		return false;
	}
}

package com.github.standobyte.jojo.gametest;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModItems;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.item.StoneMaskItem;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoneMaskUseGameTests {
	private StoneMaskUseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void oneKnifeActivatesWornStoneMask(
			GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			helper.assertTrue(helper.getLevel().addFreshEntity(player),
					"Could not add Stone Mask test player");

			ItemStack maskInput = new ItemStack(ModItems.STONE_MASK.get());
			ItemStack knife = new ItemStack(ModItems.KNIFE.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, maskInput);
			InteractionResultHolder<ItemStack> equipResult =
					ModItems.STONE_MASK.get().use(
							helper.getLevel(), player,
							InteractionHand.MAIN_HAND);
			ItemStack equippedMask =
					player.getItemBySlot(EquipmentSlot.HEAD);
			helper.assertTrue(equipResult.getResult().consumesAction(),
					"Survival Stone Mask item use was not consumed");
			helper.assertTrue(equippedMask.is(ModItems.STONE_MASK.get()),
					"Survival Stone Mask item use did not equip HEAD");
			helper.assertTrue(maskInput.isEmpty(),
					"Survival Stone Mask item use did not clear the hand");

			player.setItemInHand(InteractionHand.MAIN_HAND, knife);

			InteractionResultHolder<ItemStack> result =
					ModItems.KNIFE.get().use(
							helper.getLevel(), player,
							InteractionHand.MAIN_HAND);
			PlayerPower power = PlayerPower.get(player);

			helper.assertTrue(result.getResult().consumesAction(),
					"One-knife Stone Mask input was not consumed");
			helper.assertTrue(power != null
					&& power.getPowerType()
							== ModPlayerPowers.VAMPIRISM.get(),
					"Stone Mask did not grant Vampirism");
			helper.assertTrue(StoneMaskItem.getActivatedTicks(equippedMask) > 0,
					"Stone Mask did not enter its activated state");
			helper.assertTrue(equippedMask.getDamageValue() == 2,
					"Stone Mask activation and self-hit wear drifted: damage="
							+ equippedMask.getDamageValue() + ", max="
							+ equippedMask.getMaxDamage() + ", infinite="
							+ player.hasInfiniteMaterials());
			helper.assertTrue(knife.getCount() == 1,
					"Stone Mask activation consumed the knife");

			StoneMaskItem maskItem = (StoneMaskItem) equippedMask.getItem();
			for (int tick = 0; tick < 101; tick++) {
				maskItem.inventoryTick(equippedMask, helper.getLevel(),
						player, -1, false);
			}
			helper.assertTrue(
					player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
					"Activated Stone Mask did not leave HEAD after 101 ticks");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}
}

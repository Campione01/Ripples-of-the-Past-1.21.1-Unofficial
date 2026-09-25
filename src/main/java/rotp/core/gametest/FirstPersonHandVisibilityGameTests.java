package rotp.core.gametest;

import java.util.UUID;

import rotp.core.client.firstperson.FirstPersonRender.HandVisibility;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ClientEventHandler: cancelHandRender hid both hands while meditating; onRenderHand drew the bare main arm
 * (with the gloves layer) when gloves were held and the main hand was free.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirstPersonHandVisibilityGameTests {
	private FirstPersonHandVisibilityGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void glovesDrawTheBareMainArm(GameTestHelper helper) {
		ItemStack gloves = new ItemStack(ModItems.GLOVES.get());
		ItemStack bubbleGloves = new ItemStack(ModItems.BUBBLE_GLOVES.get());
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		helper.assertTrue(HandVisibility.drawsBareMainArmForGloves(gloves, ItemStack.EMPTY),
				"gloves in the main hand drew no arm");
		helper.assertTrue(HandVisibility.drawsBareMainArmForGloves(bubbleGloves, ItemStack.EMPTY),
				"bubble gloves in the main hand drew no arm");
		helper.assertTrue(HandVisibility.drawsBareMainArmForGloves(gloves, sword),
				"gloves with a sword in the off hand drew no arm");
		helper.assertTrue(!HandVisibility.drawsBareMainArmForGloves(ItemStack.EMPTY, gloves),
				"empty main hand is drawn by vanilla, would be drawn twice");
		helper.assertTrue(!HandVisibility.drawsBareMainArmForGloves(sword, gloves),
				"a held sword was replaced by the bare arm");
		helper.assertTrue(!HandVisibility.drawsBareMainArmForGloves(sword, ItemStack.EMPTY),
				"bare arm drawn without gloves");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void meditationHidesTheHands(GameTestHelper helper) {
		helper.assertTrue(!HandVisibility.hidesHandsForMeditation(null), "no camera entity hid the hands");
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MeditationHands"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.setPos(origin.x, origin.y, origin.z);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the test player");
		try {
			helper.assertTrue(!HandVisibility.hidesHandsForMeditation(user), "hands hidden without Hamon");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(!HandVisibility.hidesHandsForMeditation(user), "hands hidden while not meditating");
			hamon.setIsMeditating(user, true);
			helper.assertTrue(HandVisibility.hidesHandsForMeditation(user), "hands still drawn while meditating");
			hamon.setIsMeditating(user, false);
			helper.assertTrue(!HandVisibility.hidesHandsForMeditation(user), "hands still hidden after meditation");
		}
		finally {
			user.discard();
		}
		helper.succeed();
	}
}

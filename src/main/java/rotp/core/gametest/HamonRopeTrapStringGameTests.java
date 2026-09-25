package rotp.core.gametest;

import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 HamonPowerType.tickUser: Rope Trap lays hotbar string as tripwire while sneaking on the ground.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonRopeTrapStringGameTests {
	private static final BlockPos FEET = new BlockPos(2, 2, 2);

	private HamonRopeTrapStringGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void sneakingPlacesStringFromLastHotbarStack(GameTestHelper helper) {
		Player user = createUser(helper, "RopeTrapPlace");
		try {
			PlayerPower power = grantHamon(helper, user, true);
			user.getInventory().items.set(2, new ItemStack(Items.STRING, 5));
			user.getInventory().items.set(7, new ItemStack(Items.STRING, 5));
			power.tick();
			assertBlock(helper, Blocks.TRIPWIRE, "Sneaking Rope Trap user did not lay a tripwire");
			assertCount(helper, user, 7, 4, "Tripwire did not use the last hotbar string stack");
			assertCount(helper, user, 2, 5, "An earlier hotbar string stack was used");
			// the tripwire now fills the block, so standing still lays nothing more
			power.tick();
			assertCount(helper, user, 7, 4, "String was spent again on an occupied block");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void creativeKeepsTheString(GameTestHelper helper) {
		Player user = createUser(helper, "RopeTrapCreative");
		try {
			PlayerPower power = grantHamon(helper, user, true);
			user.getAbilities().instabuild = true;
			user.getInventory().items.set(0, new ItemStack(Items.STRING, 3));
			power.tick();
			assertBlock(helper, Blocks.TRIPWIRE, "Creative Rope Trap user did not lay a tripwire");
			assertCount(helper, user, 0, 3, "Creative placement consumed string");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void placementNeedsSkillSneakGroundHotbarAndAir(GameTestHelper helper) {
		Player user = createUser(helper, "RopeTrapGates");
		try {
			PlayerPower power = grantHamon(helper, user, false);
			user.getInventory().items.set(4, new ItemStack(Items.STRING, 5));
			power.tick();
			assertNothingPlaced(helper, user, 4, "Rope Trap not learned");

			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.ROPE_TRAP.get()), "Could not grant Rope Trap");
			user.setShiftKeyDown(false);
			power.tick();
			assertNothingPlaced(helper, user, 4, "Not sneaking");

			user.setShiftKeyDown(true);
			user.setOnGround(false);
			power.tick();
			assertNothingPlaced(helper, user, 4, "Not on the ground");

			user.setOnGround(true);
			helper.setBlock(FEET, Blocks.COBWEB);
			power.tick();
			assertBlock(helper, Blocks.COBWEB, "Rope Trap replaced a non-air block");
			assertCount(helper, user, 4, 5, "String spent on a non-air block");
			helper.setBlock(FEET, Blocks.AIR);

			// string outside the hotbar is not used
			user.getInventory().items.set(9, user.getInventory().items.get(4));
			user.getInventory().items.set(4, ItemStack.EMPTY);
			power.tick();
			assertBlock(helper, Blocks.AIR, "String outside the hotbar laid a tripwire");
			assertCount(helper, user, 9, 5, "String outside the hotbar was spent");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	private static Player createUser(GameTestHelper helper, String name) {
		helper.setBlock(FEET.below(), Blocks.STONE);
		helper.setBlock(FEET, Blocks.AIR);
		helper.setBlock(FEET.above(), Blocks.AIR);
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		BlockPos feet = helper.absolutePos(FEET);
		user.setPos(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
		user.getAbilities().instabuild = false;
		user.getAbilities().flying = false;
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add Rope Trap test player");
		user.getInventory().clearContent();
		user.setOnGround(true);
		user.setShiftKeyDown(true);
		return user;
	}

	private static PlayerPower grantHamon(GameTestHelper helper, Player user, boolean learnRopeTrap) {
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
		if (learnRopeTrap) {
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.ROPE_TRAP.get()), "Could not grant Rope Trap");
		}
		helper.assertTrue(user.blockPosition().equals(helper.absolutePos(FEET)),
				"Test player is not standing in the fixture block");
		return power;
	}

	private static void assertNothingPlaced(GameTestHelper helper, Player user, int slot, String gate) {
		assertBlock(helper, Blocks.AIR, gate + ": a tripwire was laid");
		assertCount(helper, user, slot, 5, gate + ": string was spent");
	}

	private static void assertBlock(GameTestHelper helper, Block expected, String message) {
		helper.assertTrue(helper.getBlockState(FEET).is(expected), message + " (found "
				+ helper.getBlockState(FEET) + ")");
	}

	private static void assertCount(GameTestHelper helper, Player user, int slot, int expected, String message) {
		ItemStack stack = user.getInventory().items.get(slot);
		int count = stack.is(Items.STRING) ? stack.getCount() : 0;
		helper.assertTrue(count == expected, message + " (slot " + slot + " has " + count
				+ ", expected " + expected + ")");
	}
}

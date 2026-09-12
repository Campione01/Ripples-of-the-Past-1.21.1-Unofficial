package com.github.standobyte.jojo.gametest;

import java.util.UUID;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonMovementHelper;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonLiquidWalkingGameTests {

	private HamonLiquidWalkingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void liquidQueriesDoNotMutatePlayer(GameTestHelper helper) {
		Player user = createUser(helper, "HamonQueries");
		try {
			HamonData hamon = grantHamon(helper, user, true);
			BlockPos water = placeFluid(helper, false);
			user.setPos(water.getX() + 0.5D, surfaceY(helper, water), water.getZ() + 0.5D);
			user.setOnGround(false);
			user.fallDistance = 4.0F;
			float energy = hamon.getEnergy();
			float stability = hamon.getBreathStability();
			float health = user.getHealth();
			for (int query = 0; query < 32; query++) {
				helper.assertTrue(user.canStandOnFluid(Fluids.WATER.defaultFluidState()),
						"Eligible water collision query was rejected");
				helper.assertTrue(user.canStandOnFluid(Fluids.LAVA.defaultFluidState()),
						"Eligible lava collision query was rejected");
			}
			helper.assertTrue(!user.onGround(), "Collision query changed onGround");
			assertClose(helper, user.fallDistance, 4.0D, "Collision query reset fall distance");
			assertClose(helper, hamon.getEnergy(), energy, "Collision query consumed energy");
			assertClose(helper, hamon.getBreathStability(), stability,
					"Collision query consumed breath stability");
			assertClose(helper, user.getHealth(), health, "Collision query changed health");
			hamon.postTickWaterWalking(user);
			helper.assertTrue(!hamon.isWaterWalking(),
					"Collision query published a liquid-walking contact");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void waterMovementChargesOncePerTick(GameTestHelper helper) {
		Player user = createUser(helper, "HamonWaterMove");
		try {
			HamonData hamon = grantHamon(helper, user, true);
			BlockPos water = placeFluid(helper, false);
			landOnFluid(helper, user, water);
			assertClose(helper, hamon.getEnergy(), 450.0D,
					"First water contact did not charge exactly 50 energy");
			for (int probe = 0; probe < 16; probe++) {
				helper.assertTrue(user.canStandOnFluid(Fluids.WATER.defaultFluidState()),
						"Paid water contact lost same-tick eligibility");
				user.move(MoverType.SELF, new Vec3(0.0D, -0.04D, 0.0D));
				assertSupported(helper, user, water);
				assertClose(helper, hamon.getEnergy(), 450.0D,
						"Repeated same-tick movement charged water contact again");
			}
			hamon.postTickWaterWalking(user);
			helper.assertTrue(hamon.isWaterWalking(), "Real water contact was not published");
			user.tickCount++;
			for (int probe = 0; probe < 16; probe++) {
				user.move(MoverType.SELF, new Vec3(0.0D, -0.04D, 0.0D));
				assertSupported(helper, user, water);
				assertClose(helper, hamon.getEnergy(), 449.0D,
						"Sustained water contact must charge exactly once at 1 energy");
			}
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lastAffordableContactLastsOnlyItsTick(GameTestHelper helper) {
		Player user = createUser(helper, "HamonLastReserve");
		try {
			HamonData hamon = grantHamon(helper, user, true);
			BlockPos water = placeFluid(helper, false);
			// Zero energy alone is not exhausted: Hamon can also spend breath stability.
			hamon.setBreathStability(1.0F);
			hamon.setEnergy(0.0F);
			helper.assertTrue(hamon.hasEnergy(50.0F, user),
					"Last-reserve fixture cannot afford an initial contact");
			landOnFluid(helper, user, water);
			assertClose(helper, hamon.getEnergy(), 0.0D, "Last-reserve contact created energy");
			assertClose(helper, hamon.getBreathStability(), 0.0D,
					"Last-reserve contact did not consume the final breath reserve");
			for (int probe = 0; probe < 8; probe++) {
				helper.assertTrue(user.canStandOnFluid(Fluids.WATER.defaultFluidState()),
						"Final affordable contact lost support during its paid tick");
				user.move(MoverType.SELF, new Vec3(0.0D, -0.04D, 0.0D));
				assertSupported(helper, user, water);
			}
			hamon.postTickWaterWalking(user);
			user.tickCount++;
			helper.assertTrue(!user.canStandOnFluid(Fluids.WATER.defaultFluidState()),
					"Exhausted player retained eligibility into an unpaid tick");
			helper.assertTrue(!HamonMovementHelper.onLiquidWalkingContact(
					user, Fluids.WATER.defaultFluidState()),
					"Exhausted player committed an unpaid contact");
			user.move(MoverType.SELF, new Vec3(0.0D, -0.1D, 0.0D));
			helper.assertTrue(user.getY() < surfaceY(helper, water) - 0.05D,
					"Exhausted player did not descend through the water surface");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void liquidWalkingRejectsIneligibleStates(GameTestHelper helper) {
		Player user = createUser(helper, "HamonRejections");
		try {
			HamonData hamon = grantHamon(helper, user, false);
			assertRejected(helper, user, hamon, "Missing liquid-walking skill");
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.LIQUID_WALKING.get()),
					"Could not grant liquid-walking skill");
			hamon.setBreathStability(0.0F);
			hamon.setEnergy(0.0F);
			assertRejected(helper, user, hamon, "No energy or breath reserve");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(500.0F);
			user.setShiftKeyDown(true);
			hamon.setDoubleShiftPress(user);
			assertRejected(helper, user, hamon, "Double-shift descent");
			user.setShiftKeyDown(false);
			user.setRemainingFireTicks(100);
			helper.assertTrue(user.isOnFire(), "Fire rejection fixture is not burning");
			assertRejected(helper, user, hamon, "Burning player entering water");
			helper.assertTrue(user.canStandOnFluid(Fluids.LAVA.defaultFluidState()),
					"Water-only fire rejection also disabled lava walking");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void lavaDamageRequiresContactAndOccursOncePerTick(GameTestHelper helper) {
		// Factory FakePlayer is permanently invulnerable, so health assertions need the vanilla mock.
		Player user = helper.makeMockPlayer(GameType.SURVIVAL);
		try {
			addUser(helper, user);
			HamonData hamon = grantHamon(helper, user, true);
			BlockPos lava = placeFluid(helper, true);
			user.setPos(lava.getX() + 0.5D, surfaceY(helper, lava), lava.getZ() + 0.5D);
			user.setOnGround(false);
			float health = user.getHealth();
			for (int query = 0; query < 16; query++) {
				user.invulnerableTime = 0;
				helper.assertTrue(user.canStandOnFluid(Fluids.LAVA.defaultFluidState()),
						"Eligible lava query was rejected");
				assertClose(helper, user.getHealth(), health, "Lava query caused damage");
				assertClose(helper, hamon.getEnergy(), 500.0D, "Lava query consumed energy");
			}
			user.invulnerableTime = 0;
			landOnFluid(helper, user, lava);
			assertClose(helper, user.getHealth(), health - 1.0D,
					"Actual unprotected lava contact did not cause exactly 1 damage");
			for (int contact = 0; contact < 16; contact++) {
				// Remove vanilla damage immunity so it cannot conceal repeated damage calls.
				user.invulnerableTime = 0;
				user.move(MoverType.SELF, new Vec3(0.0D, -0.04D, 0.0D));
				assertSupported(helper, user, lava);
				assertClose(helper, user.getHealth(), health - 1.0D,
						"Repeated same-tick lava contact caused extra damage");
				assertClose(helper, hamon.getEnergy(), 450.0D,
						"Repeated same-tick lava contact consumed extra energy");
			}
			hamon.postTickWaterWalking(user);
			user.tickCount++;
			user.invulnerableTime = 0;
			user.move(MoverType.SELF, new Vec3(0.0D, -0.04D, 0.0D));
			assertSupported(helper, user, lava);
			assertClose(helper, user.getHealth(), health - 2.0D,
					"Next-tick lava contact did not apply its own damage");
			assertClose(helper, hamon.getEnergy(), 449.0D,
					"Next-tick lava contact did not use the sustained cost");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	private static Player createUser(GameTestHelper helper, String name) {
		Player user = FakePlayerFactory.get(helper.getLevel(),
				new GameProfile(UUID.randomUUID(), name));
		addUser(helper, user);
		return user;
	}

	private static void addUser(GameTestHelper helper, Player user) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 4, 2));
		user.setPos(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D);
		user.getAbilities().instabuild = false;
		user.getAbilities().flying = false;
		user.getAbilities().invulnerable = false;
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user),
				"Could not add liquid-walking test player");
	}

	private static HamonData grantHamon(GameTestHelper helper, Player user, boolean learnSkill) {
		PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
		power.setPowerType(ModPlayerPowers.HAMON.get());
		HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
		if (learnSkill) {
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.LIQUID_WALKING.get()),
					"Could not grant liquid-walking skill");
		}
		hamon.setBreathStability(hamon.getMaxBreathStability());
		hamon.setEnergy(500.0F);
		assertClose(helper, hamon.getEnergy(), 500.0D, "Initial Hamon energy fixture is incorrect");
		return hamon;
	}

	private static BlockPos placeFluid(GameTestHelper helper, boolean lava) {
		BlockPos relative = new BlockPos(2, 2, 2);
		helper.setBlock(relative.below(), Blocks.STONE);
		helper.setBlock(relative, lava ? Blocks.LAVA : Blocks.WATER);
		helper.setBlock(relative.above(), Blocks.AIR);
		helper.setBlock(relative.above(2), Blocks.AIR);
		helper.setBlock(relative.above(3), Blocks.AIR);
		return helper.absolutePos(relative);
	}

	private static double surfaceY(GameTestHelper helper, BlockPos fluidPos) {
		FluidState fluid = helper.getLevel().getFluidState(fluidPos);
		return fluidPos.getY() + fluid.getHeight(helper.getLevel(), fluidPos);
	}

	private static void landOnFluid(GameTestHelper helper, Player user, BlockPos fluidPos) {
		user.setPos(fluidPos.getX() + 0.5D, surfaceY(helper, fluidPos) + 0.25D,
				fluidPos.getZ() + 0.5D);
		user.setOnGround(false);
		user.fallDistance = 4.0F;
		user.move(MoverType.SELF, new Vec3(0.0D, -0.5D, 0.0D));
		assertSupported(helper, user, fluidPos);
		assertClose(helper, user.fallDistance, 0.0D, "Actual liquid contact did not reset fall distance");
	}

	private static void assertSupported(GameTestHelper helper, Player user, BlockPos fluidPos) {
		helper.assertTrue(user.onGround(), "Real movement did not ground the player on liquid");
		assertClose(helper, user.getY(), surfaceY(helper, fluidPos),
				"Real movement did not stop at the liquid surface");
	}

	private static void assertRejected(GameTestHelper helper, Player user, HamonData hamon,
			String condition) {
		float energy = hamon.getEnergy();
		float stability = hamon.getBreathStability();
		boolean onGround = user.onGround();
		helper.assertTrue(!user.canStandOnFluid(Fluids.WATER.defaultFluidState()),
				condition + " was accepted by the collision query");
		helper.assertTrue(!HamonMovementHelper.onLiquidWalkingContact(
				user, Fluids.WATER.defaultFluidState()),
				condition + " was accepted by the contact commit");
		assertClose(helper, hamon.getEnergy(), energy, condition + " consumed energy");
		assertClose(helper, hamon.getBreathStability(), stability,
				condition + " consumed breath stability");
		helper.assertTrue(user.onGround() == onGround, condition + " changed onGround");
	}

	private static void assertClose(GameTestHelper helper, double actual, double expected,
			String message) {
		helper.assertTrue(Math.abs(actual - expected) < 0.0001D,
				message + ": expected=" + expected + ", actual=" + actual);
	}
}

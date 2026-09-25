package rotp.core.gametest;

import java.util.Arrays;

import rotp.core.block.WoodenCoffinBlock;
import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.init.ModBlocks;
import rotp.core.util.mod.HumanoidArmPoses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 BipedModelMixin: arms raised on the leaves glider, arms straight while sleeping in a coffin
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HumanoidArmPoseGameTests {
	private static final float PI = (float) Math.PI;

	private HumanoidArmPoseGameTests() {}

	private static float[] startPose() {
		return new float[] { 0.3F, 0.4F, 0.5F, 0.6F, 0.7F, 0.8F, 0.9F };
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void leavesGliderRiderRaisesArms(GameTestHelper helper) {
		Zombie rider = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 2, 2));
		BlockPos gliderPos = helper.absolutePos(new BlockPos(1, 3, 1));
		LeavesGliderEntity glider = new LeavesGliderEntity(helper.getLevel());
		try {
			float[] pose = startPose();
			helper.assertFalse(HumanoidArmPoses.apply(rider, pose), "Arm pose applied while not riding");
			helper.assertTrue(Arrays.equals(pose, startPose()), "Arm pose changed while not riding: " + Arrays.toString(pose));

			// another vehicle keeps the vanilla riding pose
			helper.assertTrue(rider.startRiding(pig, true), "Could not mount the pig");
			helper.assertFalse(HumanoidArmPoses.apply(rider, pose), "Glider arm pose applied on a pig");
			helper.assertTrue(Arrays.equals(pose, startPose()), "Arm pose changed on a pig: " + Arrays.toString(pose));
			rider.stopRiding();

			glider.moveTo(gliderPos.getX() + 0.5, gliderPos.getY(), gliderPos.getZ() + 0.5, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(glider), "Could not add the glider");
			helper.assertTrue(rider.startRiding(glider, true) && rider.getVehicle() == glider, "Could not mount the glider");
			helper.assertTrue(HumanoidArmPoses.ridingLeavesGlider(rider), "Glider rider not detected");
			helper.assertTrue(HumanoidArmPoses.apply(rider, pose), "Glider arm pose did not apply");
			float[] expected = { PI, 0.0F, 0.0F, PI, 0.0F, 0.0F, 0.0F };
			helper.assertTrue(Arrays.equals(pose, expected),
					"Glider arm pose: " + Arrays.toString(pose) + ", expected " + Arrays.toString(expected));
			helper.succeed();
		}
		finally {
			rider.stopRiding();
			glider.discard();
			rider.discard();
			pig.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void coffinSleeperHoldsArmsStraight(GameTestHelper helper) {
		BlockPos headRel = new BlockPos(1, 2, 1);
		BlockPos footRel = headRel.relative(Direction.SOUTH);
		BlockState coffin = ModBlocks.WOODEN_COFFIN_OAK.values().iterator().next().get().defaultBlockState()
				.setValue(WoodenCoffinBlock.FACING, Direction.NORTH);
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
		BlockPos head = helper.absolutePos(headRel);
		Zombie sleeper = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		try {
			// a vanilla bed keeps the vanilla sleeping arms
			helper.setBlock(headRel, bed.setValue(BedBlock.PART, BedPart.HEAD));
			helper.setBlock(footRel, bed.setValue(BedBlock.PART, BedPart.FOOT));
			sleeper.startSleeping(head);
			helper.assertTrue(sleeper.isSleeping(), "Zombie did not fall asleep in the bed");
			float[] pose = startPose();
			helper.assertFalse(HumanoidArmPoses.apply(sleeper, pose), "Coffin arm pose applied in a bed");
			helper.assertTrue(Arrays.equals(pose, startPose()), "Arm pose changed in a bed: " + Arrays.toString(pose));
			sleeper.stopSleeping();

			// clear the bed first so its halves do not break the new coffin halves
			helper.setBlock(headRel, Blocks.AIR);
			helper.setBlock(footRel, Blocks.AIR);
			helper.setBlock(headRel, coffin.setValue(BedBlock.PART, BedPart.HEAD));
			helper.setBlock(footRel, coffin.setValue(BedBlock.PART, BedPart.FOOT));
			sleeper.startSleeping(head);
			helper.assertTrue(sleeper.isSleeping(), "Zombie did not fall asleep in the coffin");
			helper.assertTrue(HumanoidArmPoses.sleepingInCoffin(sleeper), "Coffin sleeper not detected");
			helper.assertTrue(HumanoidArmPoses.apply(sleeper, pose), "Coffin arm pose did not apply");
			// x and z rotations zeroed; arm y and body z kept
			float[] expected = { 0.0F, 0.4F, 0.0F, 0.0F, 0.7F, 0.0F, 0.9F };
			helper.assertTrue(Arrays.equals(pose, expected),
					"Coffin arm pose: " + Arrays.toString(pose) + ", expected " + Arrays.toString(expected));

			// awake next to the coffin: no pose
			sleeper.stopSleeping();
			float[] awake = startPose();
			helper.assertFalse(HumanoidArmPoses.apply(sleeper, awake), "Coffin arm pose applied after waking");
			helper.succeed();
		}
		finally {
			if (sleeper.isSleeping()) {
				sleeper.stopSleeping();
			}
			sleeper.discard();
			helper.setBlock(headRel, Blocks.AIR);
			helper.setBlock(footRel, Blocks.AIR);
		}
	}
}

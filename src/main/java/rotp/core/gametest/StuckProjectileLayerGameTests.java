package rotp.core.gametest;

import rotp.core.client.layer.StuckProjectiles;
import rotp.core.client.layer.StuckProjectiles.Type;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.GEStuckObjectsState;
import rotp.core.init.ModDataAttachmentTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 KnifeLayer / MobStuckArrowLayer data and placement math behind the stuck arrow and knife layers.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StuckProjectileLayerGameTests {
	private StuckProjectileLayerGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void stuckCountsReadArrowsAndKnives(GameTestHelper helper) {
		Pig pig = helper.spawn(EntityType.PIG, 1, 2, 1);
		try {
			helper.assertTrue(StuckProjectiles.numStuck(Type.KNIFE, pig) == 0
					&& StuckProjectiles.numStuck(Type.ARROW, pig) == 0, "Fresh mob reports stuck objects");
			helper.assertTrue(!pig.hasData(ModDataAttachmentTypes.GE_STUCK_OBJECTS_STATE),
					"Reading the stuck knife count created the attachment");
			pig.setArrowCount(3);
			GEStuckObjectsState.get(pig).setStuckKnives(2);
			helper.assertTrue(StuckProjectiles.numStuck(Type.ARROW, pig) == 3,
					"Arrow count not read: " + StuckProjectiles.numStuck(Type.ARROW, pig));
			helper.assertTrue(StuckProjectiles.numStuck(Type.KNIFE, pig) == 2,
					"Stuck knife count not read: " + StuckProjectiles.numStuck(Type.KNIFE, pig));
			helper.assertTrue(StuckProjectiles.seed(pig, Type.ARROW) == pig.getId()
					&& StuckProjectiles.seed(pig, Type.KNIFE) == pig.getId() + 1L,
					"Seeds are not entity id + type ordinal");
			helper.succeed();
		}
		finally {
			pig.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void cubePointOnFaceOrSlimeCore(GameTestHelper helper) {
		boolean[] faces = new boolean[6];
		for (long seed = 0; seed < 300; seed++) {
			float[] p = StuckProjectiles.cubePoint(RandomSource.create(seed), false);
			int onFace = -1;
			for (int axis = 0; axis < 3; axis++) {
				helper.assertTrue(p[axis] >= 0 && p[axis] <= 1, "Point outside the cube: seed " + seed);
				if (p[axis] == 0 || p[axis] == 1) {
					onFace = axis * 2 + (int) p[axis];
				}
			}
			helper.assertTrue(onFace >= 0, "Point not on a cube face: seed " + seed);
			faces[onFace] = true;
			float[] s = StuckProjectiles.cubePoint(RandomSource.create(seed), true);
			for (int axis = 0; axis < 3; axis++) {
				helper.assertTrue(s[axis] >= 0.25F && s[axis] <= 0.75F, "Slime point outside the inner half: seed " + seed);
			}
		}
		for (int face = 0; face < 6; face++) {
			helper.assertTrue(faces[face], "Cube face never used: " + face);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void projectileAnglesPointIntoBody(GameTestHelper helper) {
		helper.assertTrue(StuckProjectiles.direction(0) == 1 && StuckProjectiles.direction(1) == -1
				&& StuckProjectiles.direction(0.5F) == 0, "Face coordinate to direction mapping changed");
		helper.assertTrue(near(StuckProjectiles.yRot(1, 0), 90) && near(StuckProjectiles.yRot(0, 1), 0)
				&& near(StuckProjectiles.yRot(-1, 0), -90), "Yaw is not atan2(x, z)");
		helper.assertTrue(near(StuckProjectiles.xRot(0, 1, 0), 90) && near(StuckProjectiles.xRot(1, 0, 0), 0)
				&& near(StuckProjectiles.xRot(0, 1, 1), 45) && near(StuckProjectiles.xRot(3, -4, 4), -38.6598F),
				"Pitch is not atan2(y, horizontal)");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void weightedCubeAndScaleBack(GameTestHelper helper) {
		helper.assertTrue(near(StuckProjectiles.cubeArea(0, 0, 0, 8, 8, 8), 384)
				&& near(StuckProjectiles.cubeArea(-2, 0, -2, 2, 12, 2), 224), "Cube surface area changed");
		float[] areas = { 2, 0, 6 };
		helper.assertTrue(StuckProjectiles.pickWeighted(areas, 0) == 0
				&& StuckProjectiles.pickWeighted(areas, 1.99F) == 0
				&& StuckProjectiles.pickWeighted(areas, 2) == 2
				&& StuckProjectiles.pickWeighted(areas, 7.9F) == 2
				&& StuckProjectiles.pickWeighted(areas, 9) == 2, "Area-weighted cube pick changed");
		float[] back = StuckProjectiles.scaleBack(0.5F, 1, 2);
		helper.assertTrue(near(back[0], 1.875F) && near(back[1], 0.9375F) && near(back[2], 0.46875F),
				"Scale-back does not restore player scale");
		helper.assertTrue(near(StuckProjectiles.scaleBack(0, 0, 0)[0], StuckProjectiles.PLAYER_SCALE),
				"Zero renderer scale not guarded");
		helper.succeed();
	}

	private static boolean near(float value, float expected) {
		return Math.abs(value - expected) < 1.0E-3F;
	}
}

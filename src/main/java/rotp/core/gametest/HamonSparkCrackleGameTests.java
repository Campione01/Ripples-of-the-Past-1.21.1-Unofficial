package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonBubbleEntity;
import rotp.core.impl.powers.hamon.entity.HamonProjectileShieldEntity;
import rotp.core.init.ModEntityTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 client ticks: a Hamon bubble crackled once every 10 ticks, staggered by entity id, and the Projectile Shield
 * drew (int) (width * height * 0.1) spark particles a tick at uniform points of its plane. The sounds themselves are
 * client-only and checked in game.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonSparkCrackleGameTests {
	private HamonSparkCrackleGameTests() {}

	@GameTest(template = "empty")
	public static void bubbleCracklesOnceEveryTenTicksByEntityId(GameTestHelper helper) {
		for (int id : new int[] { 13, 20, 7 }) {
			List<Integer> ticks = new ArrayList<>();
			for (int tick = 0; tick < 30; tick++) {
				if (HamonBubbleEntity.isSparkSoundTick(tick, id)) {
					ticks.add(tick);
				}
			}
			int first = id % 10;
			helper.assertTrue(ticks.equals(List.of(first, first + 10, first + 20)),
					"bubble " + id + " must crackle on ticks " + first + ", " + (first + 10) + ", " + (first + 20) + ": " + ticks);
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void shieldSparksCoverTheWholeDrawnPlane(GameTestHelper helper) {
		HamonProjectileShieldEntity shield = new HamonProjectileShieldEntity(ModEntityTypes.HAMON_PROJECTILE_SHIELD.get(), helper.getLevel());
		helper.assertTrue(shield.sparkParticleCount() == 3, "an 8 x 4 shield must spark 3 times a tick: " + shield.sparkParticleCount());
		shield.refresh(null, 10.0F, 5.0F);
		helper.assertTrue(shield.sparkParticleCount() == 5, "a 10 x 5 shield must spark 5 times a tick: " + shield.sparkParticleCount());

		shield.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
		shield.setYRot(30.0F);
		shield.setXRot(-20.0F);
		// the renderer's plane axes
		Vec3 facing = Vec3.directionFromRotation(-20.0F, 30.0F).normalize();
		Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(facing).normalize();
		Vec3 up = facing.cross(right).normalize();
		RandomSource rand = RandomSource.create(148L);
		double minRight = Double.MAX_VALUE, maxRight = -Double.MAX_VALUE;
		double minUp = Double.MAX_VALUE, maxUp = -Double.MAX_VALUE;
		for (int i = 0; i < 400; i++) {
			Vec3 d = shield.randomPlanePoint(rand).subtract(shield.position());
			helper.assertTrue(Math.abs(d.dot(facing)) < 1.0E-6D, "a spark left the shield plane: " + d.dot(facing));
			double r = d.dot(right);
			double u = d.dot(up);
			helper.assertTrue(Math.abs(r) <= 5.0D + 1.0E-6D && Math.abs(u) <= 2.5D + 1.0E-6D,
					"a spark fell outside the 10 x 5 shield: " + r + ", " + u);
			minRight = Math.min(minRight, r);
			maxRight = Math.max(maxRight, r);
			minUp = Math.min(minUp, u);
			maxUp = Math.max(maxUp, u);
		}
		helper.assertTrue(minRight < -4.0D && maxRight > 4.0D && minUp < -2.0D && maxUp > 2.0D,
				"sparks did not spread over the whole shield: right " + minRight + ".." + maxRight + ", up " + minUp + ".." + maxUp);
		helper.succeed();
	}
}

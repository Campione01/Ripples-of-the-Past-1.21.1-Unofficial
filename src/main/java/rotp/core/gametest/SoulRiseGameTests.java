package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.subsystems.soul.SoulEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 SoulEntity.getDeltaMovement returned a constant (0, 0.04, 0): the soul, and the camera of a player who watches
 * it, rose 0.04 blocks a tick, 8 blocks over its 200 ticks, also in the port's resolve-up phase of its last 60 ticks.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoulRiseGameTests {
	private static final int WAIT = 40;

	private SoulRiseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = "soul_rise")
	public static void soulRisesFourHundredthsOfABlockATick(GameTestHelper helper) {
		Cow origin = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(origin != null, "Fixture: could not create the cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		origin.setNoAi(true);
		origin.setNoGravity(true);
		origin.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(origin), "Fixture: could not add the cow");
		// dying, so that its soul stays; the soul holds its death animation short of removal
		origin.setHealth(0.0F);
		// past 20 ticks the soul is in its last 60, the port's resolve-up phase
		SoulEntity soul = new SoulEntity(helper.getLevel(), origin, 70);
		helper.assertTrue(helper.getLevel().addFreshEntity(soul), "Fixture: could not add the soul");
		double startY = soul.getY();
		helper.runAfterDelay(WAIT, () -> {
			try {
				helper.assertTrue(!soul.isRemoved() && soul.tickCount >= WAIT / 2,
						"Fixture: the soul is gone or did not tick: removed=" + soul.isRemoved() + " ticks=" + soul.tickCount);
				double rose = soul.getY() - startY;
				double expected = 0.04D * soul.tickCount;
				helper.assertTrue(Math.abs(rose - expected) < 1.0E-3D, "The soul rose " + rose + " blocks in " + soul.tickCount
						+ " ticks; 1.16 rose a constant 0.04 a tick, " + expected);
			}
			finally {
				soul.discard();
				origin.discard();
			}
			helper.succeed();
		});
	}
}

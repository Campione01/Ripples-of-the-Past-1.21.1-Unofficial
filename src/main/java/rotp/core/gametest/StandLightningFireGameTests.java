package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity: thunderHit and lavaHurt did nothing, setSecondsOnFire put the Stand's fire out, and only a Stand's
 * fire set a Stand alight (setFireFromStand, which DamageUtil.setOnFire took for the attacks that pass stand = true).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandLightningFireGameTests {

	private StandLightningFireGameTests() {}

	/*
	 * A lightning bolt struck every living entity within 3 blocks of it through Entity.thunderHit, which added a tick to
	 * its fire, set it alight from 0 and hurt it; 1.16's StandEntity.thunderHit did nothing, for a Stand beside its user,
	 * one away from it, or one a Stand's fire already burned. A cow beside the Stand shows the bolt struck.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandIgnoresALightningBoltAsIn116(GameTestHelper helper) {
		String[] rows = { "a star_platinum beside its user", "a hierophant_green 6 blocks away from its user",
				"a star_platinum a Stand's fire burns" };
		for (String row : rows) {
			boolean away = row.contains("away");
			boolean burning = row.endsWith("burns");
			StandTaskTargetOffsetGameTests.withStand(helper, away ? "hierophant_green" : "star_platinum", scene -> {
				StandEntity stand = scene.stand();
				if (away) {
					stand.setCanFollowUser(false);
					stand.setNoGravity(true);
					Vec3 at = scene.user().position().add(6.0D, 0.0D, 0.0D);
					stand.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
				}
				Cow cow = scene.cow(stand.position().subtract(scene.user().position()).add(0.0D, 0.0D, 1.0D));
				LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
				helper.assertTrue(bolt != null, "Could not create the lightning bolt");
				bolt.moveTo(stand.getX(), stand.getY(), stand.getZ());
				helper.assertTrue(!stand.isOnFire() && !cow.isOnFire() && (!away || scene.user().distanceTo(stand) > 5.0D),
						"setup: something is already burning, or the user stands within the bolt's reach of a Stand away from it");
				if (burning) {
					DamageUtil.setOnFire(stand, 100, true);
				}
				int fireBefore = stand.getRemainingFireTicks();
				try {
					bolt.tick();
					helper.assertTrue(cow.isOnFire(), "setup: the bolt did not strike the cow beside " + row);
					helper.assertTrue(stand.getRemainingFireTicks() == fireBefore && stand.isOnFire() == burning,
							"1.16 StandEntity.thunderHit left " + row + " that a lightning bolt struck as it was, " + fireBefore
									+ " fire ticks: it has " + stand.getRemainingFireTicks());
				}
				finally {
					bolt.discard();
				}
				stand.clearFire();
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 kept a Stand from catching fire by anything but a Stand's fire: lava (lavaHurt did nothing) and a fire block did
	 * not set it alight, nor did an ordinary ignition (setSecondsOnFire put the fire out), while a Stand's fire
	 * (DamageUtil.setOnFire with stand = true, setFireFromStand) did; a Stand a Stand's fire had set alight kept burning in
	 * lava and in a fire block, which rekindles only an entity whose fire ticks reach 0.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandCatchesFireOnlyFromAStandsFireAsIn116(GameTestHelper helper) {
		String[] rows = { "in a fire block", "in lava", "an ordinary ignition", "a Stand's fire",
				"a Stand's fire, then in a fire block", "a Stand's fire, then in lava" };
		for (String row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandEntity stand = scene.stand();
				BlockPos at = stand.blockPosition();
				helper.assertTrue(!stand.isOnFire(), "setup: the Stand is already burning");
				boolean standsFire = row.startsWith("a Stand's fire");
				if (standsFire) {
					DamageUtil.setOnFire(stand, 100, true);
				}
				if (row.endsWith("in lava")) {
					for (int tick = 0; tick < 5; tick++) {
						stand.lavaHurt();
					}
				}
				else if (row.endsWith("in a fire block")) {
					for (int tick = 0; tick < 5; tick++) {
						Blocks.FIRE.defaultBlockState().entityInside(helper.getLevel(), at, stand);
					}
				}
				else if (row.equals("an ordinary ignition")) {
					DamageUtil.setOnFire(stand, 100, false);
					stand.igniteForSeconds(5.0F);
				}
				helper.assertTrue(stand.isOnFire() == standsFire, "1.16 " + (standsFire ? "set" : "did not set") + " a star_platinum"
						+ " alight by " + row + ": it burns for " + stand.getRemainingFireTicks() + " ticks");
				stand.clearFire();
			});
		}
		helper.succeed();
	}
}

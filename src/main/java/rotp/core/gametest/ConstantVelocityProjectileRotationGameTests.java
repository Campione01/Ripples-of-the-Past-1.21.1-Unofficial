package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 ModdedProjectileEntity.moveProjectile turned a projectile towards its motion only when it had no constant
// velocity; every other projectile kept the rotation it was launched with, whatever happened to its motion later.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstantVelocityProjectileRotationGameTests {
	private ConstantVelocityProjectileRotationGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void deflectedBulletKeepsItsLaunchRotation(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player shooter = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		TommyGunBulletEntity bullet = null;
		try {
			// the whole flight goes up, clear of every test plot
			Vec3 feet = helper.absoluteVec(new Vec3(0.5D, 3.0D, 0.5D));
			shooter.setNoGravity(true);
			shooter.moveTo(feet.x, feet.y, feet.z, 0.0F, -80.0F);
			helper.assertTrue(level.addFreshEntity(shooter), "Could not add the shooter");

			// launched as TommyGunItem does
			bullet = new TommyGunBulletEntity(shooter, level);
			Vec3 muzzle = shooter.getEyePosition(1.0F).subtract(0.0D, bullet.getBbHeight() / 2.0D, 0.0D).add(shooter.getLookAngle());
			bullet.shootFromRotation(shooter, 2.0F, 0.0F);
			bullet.setPos(muzzle.x, muzzle.y, muzzle.z);
			helper.assertTrue(level.addFreshEntity(bullet), "Could not add the bullet");
			float launchYaw = bullet.getYRot();
			float launchPitch = bullet.getXRot();
			Vec3 launchMotion = bullet.getDeltaMovement();

			bullet.tickCount = 0;
			bullet.tick();
			helper.assertTrue(!bullet.isRemoved() && bullet.getDeltaMovement().equals(launchMotion)
					&& bullet.getYRot() == launchYaw && bullet.getXRot() == launchPitch,
					"Fixture: a straight tick changed the bullet: motion " + launchMotion + " -> " + bullet.getDeltaMovement()
							+ ", yaw " + launchYaw + " -> " + bullet.getYRot() + ", pitch " + launchPitch + " -> " + bullet.getXRot());

			// deflected as by StandEntity.deflectSilverChariotProjectile: same speed, another direction
			Vec3 deflected = Vec3.directionFromRotation(-60.0F, 90.0F).scale(launchMotion.length());
			bullet.setDeltaMovement(deflected);
			bullet.setIsDeflected(deflected, bullet.position());
			for (int tick = 1; tick <= 3; tick++) {
				bullet.tick();
				helper.assertTrue(!bullet.isRemoved() && bullet.getDeltaMovement().equals(deflected),
						"Fixture: the deflected bullet did not keep flying: " + bullet.getDeltaMovement());
				helper.assertTrue(bullet.getYRot() == launchYaw && bullet.getXRot() == launchPitch,
						"1.16: a constant-velocity projectile keeps its launch rotation when its motion changes, but "
								+ tick + " tick(s) after the deflection yaw went " + launchYaw + " -> " + bullet.getYRot()
								+ " and pitch " + launchPitch + " -> " + bullet.getXRot());
			}
			helper.succeed();
		}
		finally {
			if (bullet != null && !bullet.isRemoved()) {
				bullet.discard();
			}
			shooter.discard();
		}
	}
}

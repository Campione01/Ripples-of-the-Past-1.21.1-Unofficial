package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDBlockBulletEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDBloodCutterEntity;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.impl.stands.magiciansred.MRFireballEntity;
import rotp.core.impl.stands.silverchariot.SCRapierEntity;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.getProjectileInaccuracy: every Stand shot (shootProjectile, Blood Cutter and the barriers' emeralds by
 * their own calls) spread by max((base + 1) * 8 / max(precision, 4) - 1, 0) of the Stand's precision, its efficiency
 * included. A shot of base 0 spreads too below a precision of 8.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandShotSpreadGameTests {
	// vanilla Projectile.getMovementToShoot: each axis of the unit direction moves by up to 0.0172275 times the spread
	private static final double SPREAD_PER_AXIS = 0.0172275D;

	private StandShotSpreadGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theStandShotSpreadIsThe116Formula(GameTestHelper helper) {
		// precision, base, the spread 1.16 gave
		double[][] rows = {
				{ 8, 0, 0 }, { 6, 0, 1.0D / 3.0D }, { 4, 0, 1 }, { 2, 0, 1 },
				{ 4, 0.25, 1.5 }, { 12, 0.25, 0 }, { 12, 1, 1.0D / 3.0D }, { 16, 1, 0 },
				{ 20, 2, 0.2 }, { 24, 2, 0 }, { 20, 8, 2.6 }, { 72, 8, 0 } };
		for (double[] row : rows) {
			float spread = StandStatFormulas.standShotInaccuracy(row[0], (float) row[1]);
			helper.assertTrue(Math.abs(spread - row[2]) < 1.0E-5D, "1.16 StandEntity.getProjectileInaccuracy(" + row[1]
					+ ") at a precision of " + row[0] + " was " + row[2] + "; the port gives " + spread);
		}
		helper.succeed();
	}

	/*
	 * Each core Stand shot that took 1.16's getProjectileInaccuracy, at a precision where 1.16 gave no spread or one the
	 * port's own curve does not: Rapier Launch (base 0) spreads below 8, Blood Cutter (1) and the concentrated Emerald Splash
	 * (1) have none from 16, Block Bullet (0.25) none from 10, the Fireball (2) none from 24, the Emerald Splash (8) spreads
	 * at 20.
	 */
	@GameTest(template = "empty", timeoutTicks = 200)
	public static void coreStandShotsSpreadByTheStandsPrecisionAsIn116(GameTestHelper helper) {
		Object[][] shots = {
				{ "silver_chariot", "rapier_launch", SCRapierEntity.class, 4.0D, 1.0D, false },
				{ "crazy_diamond", "blood_cutter", CrazyDBloodCutterEntity.class, 16.0D, 0.0D, true },
				{ "crazy_diamond", "block_bullet", CrazyDBlockBulletEntity.class, 12.0D, 0.0D, false },
				{ "magicians_red", "fireball", MRFireballEntity.class, 24.0D, 0.0D, false },
				{ "hierophant_green", "emerald_splash", HGEmeraldEntity.class, 20.0D, 2.6D, false },
				{ "hierophant_green", "emerald_splash_concentrated", HGEmeraldEntity.class, 16.0D, 0.0D, false } };
		for (Object[] shot : shots) {
			String skill = (String) shot[1];
			@SuppressWarnings("unchecked")
			Class<? extends ModdedProjectileEntity> projectileClass = (Class<? extends ModdedProjectileEntity>) shot[2];
			double precision = (double) shot[3];
			double spread = (double) shot[4];
			boolean fromUser = (boolean) shot[5];
			StandTaskTargetOffsetGameTests.withStand(helper, (String) shot[0], scene -> {
				scene.user().getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
				StandEntity.updateStandStatAttributes(scene.stand(), scene.user());
				scene.stand().getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
				helper.assertTrue(Math.abs(scene.stand().getPrecision() - precision) < 1.0E-9D,
						"setup: the " + shot[0] + "'s precision is " + scene.stand().getPrecision() + ", not " + precision);
				scene.user().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 16));
				StandTaskTargetOffsetGameTests.turn(scene.user(), 30.0F, 30.0F);
				scene.user().setXRot(-10.0F);
				scene.stand().setYRot(-40.0F);
				scene.stand().setXRot(15.0F);
				Entity shooter = fromUser ? scene.user() : scene.stand();
				EntityActionInstance action = scene.set(skill);
				List<ModdedProjectileEntity> shotsMade = new ArrayList<>();
				List<Vec3> aims = new ArrayList<>();
				for (int tick = 0; tick < 60 && shotsMade.size() < 3 && !action.isOver(); tick++) {
					LivingComponentAction.getComponent(scene.stand()).tick();
					for (ModdedProjectileEntity projectile : helper.getLevel().getEntitiesOfClass(projectileClass,
							scene.user().getBoundingBox().inflate(16.0D))) {
						if (!shotsMade.contains(projectile)) {
							shotsMade.add(projectile);
							aims.add(Vec3.directionFromRotation(shooter.getXRot(), shooter.getYRot()));
							scene.spawned().add(projectile);
						}
					}
				}
				helper.assertTrue(!shotsMade.isEmpty(), "setup: the " + skill + " of a " + shot[0] + " shot nothing");
				// the most the noise turns the unit direction, all three axes at their bound
				double noise = SPREAD_PER_AXIS * spread * Math.sqrt(3.0D);
				double maxOff = noise / (1.0D - noise) + 1.0E-6D;
				for (int i = 0; i < shotsMade.size(); i++) {
					ModdedProjectileEntity projectile = shotsMade.get(i);
					Vec3 along = aims.get(i).normalize();
					Vec3 flies = projectile.getDeltaMovement().normalize();
					// the sine of the angle between them, which a cross product keeps exact for nearly parallel ones
					double off = flies.cross(along).length();
					boolean asIn116 = spread == 0 ? off < 1.0E-6D : off > 1.0E-6D && off <= maxOff;
					helper.assertTrue(asIn116, "1.16 spread a " + shot[0] + "'s " + skill + " at a precision of " + precision
							+ " by " + spread + " (getProjectileInaccuracy); it flies " + off + " off its aim " + along
							+ " (at most " + maxOff + " for that spread)");
				}
			});
		}
		helper.succeed();
	}
}

package rotp.core.gametest;

import java.lang.reflect.Method;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 SilverChariotDashAttack.standTickPerform punched every entity its ray met through StandEntity.punch, living or not:
 * StandEntity.attackTarget gave a non-living one its punchEntity (the dash's HeavyPunchInstance, impact sound null),
 * StandEntityPunch.doHit hurt it through attackEntity's canHarm and set the dash's knockback as its motion whether it was
 * hurt or not (knockbackTarget). SilverChariotEntity.attackTarget, run by every punch of the dash, a punch that struck
 * nothing included, deflected each projectile in reach flying at the Stand within its precision cone once the stored
 * Resolve level was 4 (canDeflectProjectiles), and attackOrDeflect deflected a projectile the punch struck. Each row runs
 * one forward tick of the dash's ray with the Stand facing south and its user looking up.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverChariotDashStrike116GameTests {
	private static final String BATCH = "silver_chariot_dash_strike_116";

	private SilverChariotDashStrike116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashStrikesABoatOnItsPathAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			Boat boat = EntityType.BOAT.create(helper.getLevel());
			helper.assertTrue(boat != null, "setup: could not create the boat");
			boat.setNoGravity(true);
			boat.moveTo(stand.getX(), stand.getEyeY() - boat.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
			add(helper, scene, boat);
			dashTargets(dash, stand);
			helper.assertTrue((boat.isRemoved() || boat.getDamage() > 0.0F) && dash.punchedTarget != null
					&& dash.punchedTarget.getType() == ActionTarget.TargetType.ENTITY && dash.punchedTarget.getEntity() == boat,
					"1.16's dash punched a boat its ray met (StandEntity.attackTarget, attackEntity, hurt): the boat is removed "
							+ boat.isRemoved() + " with damage " + boat.getDamage() + ", the dash struck " + dash.punchedTarget);
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	// primed TNT takes no damage from a hit, and 1.16's knockbackTarget still set the dash's knockback as its motion
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashKnocksAsideAPrimedTntItStrikesAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			EntityActionInstance dash = dash(helper, scene, 4);
			StandEntity stand = scene.stand();
			PrimedTnt tnt = EntityType.TNT.create(helper.getLevel());
			helper.assertTrue(tnt != null, "setup: could not create the primed TNT");
			tnt.setFuse(200);
			tnt.setNoGravity(true);
			tnt.moveTo(stand.getX(), stand.getEyeY() - tnt.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
			add(helper, scene, tnt);
			dashTargets(dash, stand);
			Vec3 motion = tnt.getDeltaMovement();
			boolean fast = stand.getAttackSpeed() >= 24;
			double sideways = Math.toDegrees(Math.acos(Mth.clamp(motion.z / Math.max(motion.horizontalDistance(), 1.0E-9D), -1.0D, 1.0D)));
			boolean pushed = fast
					? Math.abs(motion.y - 0.125D) < 1.0E-6D && motion.horizontalDistanceSqr() < 1.0E-12D
					: Math.abs(motion.horizontalDistance() - 0.75D) < 1.0E-6D && Math.abs(motion.y) < 1.0E-9D
							&& sideways >= 60.0D - 1.0E-3D && sideways <= 90.0D + 1.0E-3D;
			helper.assertTrue(!tnt.isRemoved() && pushed && dash.punchedTarget != null && dash.punchedTarget.getEntity() == tnt,
					"1.16's dash set its knockback as the motion of a primed TNT it struck (attack speed " + stand.getAttackSpeed()
							+ ": " + (fast ? "0.125 up" : "0.75 a tick, 60 to 90 degrees off the dash line") + "): it moves " + motion
							+ " (" + sideways + " degrees off the line), the dash struck " + dash.punchedTarget);
			LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void theDashDeflectsAnArrowFlyingAtItOnlyAtResolveFourAsIn116(GameTestHelper helper) {
		String[] rows = { "Resolve 4, nothing struck", "Resolve 4, a boat struck", "Resolve 3, nothing struck" };
		for (String row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
				int resolve = row.startsWith("Resolve 4") ? 4 : 3;
				EntityActionInstance dash = dash(helper, scene, resolve);
				StandEntity stand = scene.stand();
				helper.assertTrue(scene.power().getResolveLevel() == resolve && stand.isSilverChariotRapierVisible(),
						"setup: Resolve " + scene.power().getResolveLevel() + ", rapier out " + stand.isSilverChariotRapierVisible());
				Boat boat = null;
				if (row.endsWith("a boat struck")) {
					boat = EntityType.BOAT.create(helper.getLevel());
					helper.assertTrue(boat != null, "setup: could not create the boat");
					boat.setNoGravity(true);
					boat.moveTo(stand.getX() + 0.5D, stand.getEyeY() - boat.getBbHeight() / 2.0D, stand.getZ() + 2.0D, 0.0F, 0.0F);
					add(helper, scene, boat);
				}
				Arrow arrow = EntityType.ARROW.create(helper.getLevel());
				helper.assertTrue(arrow != null, "setup: could not create the arrow");
				arrow.setNoGravity(true);
				arrow.moveTo(stand.getX() - 1.0D, stand.getEyeY(), stand.getZ() + 2.0D, 180.0F, 0.0F);
				Vec3 flight = new Vec3(0.0D, 0.0D, -1.0D);
				arrow.setDeltaMovement(flight);
				add(helper, scene, arrow);
				dashTargets(dash, stand);
				boolean struckBoat = boat != null && dash.punchedTarget != null && dash.punchedTarget.getEntity() == boat;
				helper.assertTrue(boat == null ? dash.punchedTarget == null : struckBoat,
						"setup (" + row + "): the dash struck " + dash.punchedTarget);
				Vec3 motion = arrow.getDeltaMovement();
				boolean deflected = motion.z > 0.9D;
				helper.assertTrue(deflected == (resolve >= 4) && (deflected || motion.equals(flight)),
						"1.16's dash punch deflected a projectile flying at Silver Chariot in front of it only at Resolve 4"
								+ " (SilverChariotEntity.attackTarget, canDeflectProjectiles), " + row + ": the arrow flying north"
								+ " at the Stand now moves " + motion);
				LivingComponentAction.getComponent(stand).setAction(null, scene.user(), SyncType.NO_SYNC);
			});
		}
		helper.succeed();
	}

	// the dash set on the following Silver Chariot, turned south, its user looking straight up so its aim meets nothing
	private static EntityActionInstance dash(GameTestHelper helper, Scene scene, int resolve) {
		StandEntity stand = scene.stand();
		Player user = scene.user();
		StandPower power = scene.power();
		power.setResolveLevel(resolve);
		power.setStamina(power.getMaxStamina());
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.updatePosition(user);
		Ability ability = power.getAbility("dash_attack");
		if (!(ability instanceof EntityActionType)) {
			power.getCurTypeData()._setSkillUnlocked("dash_attack", true, false);
			ability = power.getAbility("dash_attack");
		}
		helper.assertTrue(ability instanceof EntityActionType, "Missing registered dash_attack");
		EntityActionType type = (EntityActionType) ability;
		EntityActionInstance dash = type.createActionObj();
		type.initActionFromConfig(dash, helper.getLevel(), user, stand);
		dash.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike,
				"setup: the dash is not the Silver Chariot's action");
		stand.setYRot(0.0F);
		stand.setXRot(0.0F);
		stand.setYHeadRot(0.0F);
		user.setXRot(-90.0F);
		dash.punchedTarget = null;
		return dash;
	}

	private static void add(GameTestHelper helper, Scene scene, Entity entity) {
		helper.assertTrue(helper.getLevel().addFreshEntity(entity), "setup: could not add " + entity);
		scene.spawned().add(entity);
	}

	// the dash's strikes on the targets its ray meets while it moves forward
	private static void dashTargets(EntityActionInstance dash, StandEntity stand) {
		try {
			Method hit = DashStrike.class.getDeclaredMethod("hitDashTargets", Level.class, StandEntity.class);
			hit.setAccessible(true);
			hit.invoke(dash, stand.level(), stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the dash's ray hit", e);
		}
	}
}

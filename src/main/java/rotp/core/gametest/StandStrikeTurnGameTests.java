package rotp.core.gametest;

import java.lang.reflect.Method;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.silverchariot.SilverChariotDashAttackAbility.DashStrike;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.punch: before each punch, heavy punch and barrage hit, and each strike of the time-stop punch and of
 * Silver Chariot's dash, which struck through it too, StandEntity.aimWithThisOrUser turned the Stand toward what it aimed
 * at (MCUtil.rotateTowards), on the server.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandStrikeTurnGameTests {
	private static final double EPS = 1.0E-3D;

	private StandStrikeTurnGameTests() {}

	/*
	 * The turn went toward an entity's eyes, the yaw and the pitch each by at most attackSpeed / 16 x 18 degrees, the head
	 * onto the new yaw, before the hit: toward a cow in the Stand's reach, which the strike then hit, and toward one beyond
	 * it, which the punch missed.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void eachStrikeTurnsTheStandTowardItsAimByAtMostItsAttackSpeedBeforeTheHitAsIn116(GameTestHelper helper) {
		String[][] rows = { { "star_platinum", "punch" }, { "star_platinum", "heavy_punch" }, { "star_platinum", "barrage" },
				{ "the_world", "ts_punch" }, { "silver_chariot", "dash_attack" }, { "star_platinum", "punch at a cow beyond its reach" },
				{ "star_platinum", "heavy_charged" } };
		for (String[] row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, row[0], scene -> strike(helper, scene, row[0], row[1]));
		}
		helper.succeed();
	}

	private static void strike(GameTestHelper helper, Scene scene, String standId, String row) {
		StandEntity stand = scene.stand();
		Player user = scene.user();
		StandPower power = scene.power();
		boolean beyond = row.startsWith("punch at");
		boolean dash = row.equals("dash_attack");
		power.setResolveLevel(power.getMaxResolveLevel());
		power.setStamina(power.getMaxStamina());
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.updatePosition(user);
		Cow[] cowRef = { null };
		if (!dash) {
			Cow cow = scene.cow(new Vec3(0.0D, 0.0D, beyond ? 4.5D : 1.6D));
			cowRef[0] = cow;
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
			user.yRotO = user.getYRot();
			user.xRotO = user.getXRot();
			user.setYHeadRot(user.getYRot());
			stand.updatePosition(user);
			LivingComponentAction.getComponent(user).entityAim.setTarget(beyond ? ActionTarget.EMPTY : new ActionTarget(cow));
		}
		float[] atHit = { Float.NaN, Float.NaN };
		EntityActionType type = strikeType(helper, scene, beyond ? "punch" : row);
		EntityActionInstance action = switch (row) {
			case "heavy_punch" -> new StandEntityHeavyPunch(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount, float explRadius) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount, explRadius);
				}
			};
			// the port's charged heavy punch turns at its strike as the heavy punch it is modelled on (owner, wave 21)
			case "heavy_charged" -> new StandEntityChargedHeavy(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount);
				}
			};
			case "barrage" -> new StandEntityBarrage(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer);
				}
			};
			case "ts_punch" -> new TheWorldTSPunch(type) {
				@Override
				protected ActionTarget getPunchTarget(StandEntity performer) {
					return new ActionTarget(cowRef[0]);
				}

				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer, DamageSource dmgSource,
						float dmgAmount, float explRadius) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer, dmgSource, dmgAmount, explRadius);
				}
			};
			case "dash_attack" -> type.createActionObj();
			default -> new StandEntityPunch(type) {
				@Override
				protected void hitEntity(ActionTarget target, Level level, StandEntity performer) {
					noteStrike(atHit, performer);
					super.hitEntity(target, level, performer);
				}
			};
		};
		type.initActionFromConfig(action, helper.getLevel(), user, stand);
		action.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == action && (!dash || action instanceof DashStrike),
				"setup: the " + row + " is not the " + standId + "'s action");
		if (dash) {
			// once the dash has put the Stand in front of its user: on the Stand's look south, its middle at the Stand's
			// eyes, so that the dash's ray meets it
			stand.setYRot(0.0F);
			stand.setXRot(0.0F);
			stand.setYHeadRot(0.0F);
			cowRef[0] = scene.cow(new Vec3(stand.getX(), stand.getEyeY() - 0.7D, stand.getZ() + 2.0D).subtract(user.position()));
		}
		Cow cow = cowRef[0];

		Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
		float aimYaw = MathUtil.yRotDegFromVec(toEyes);
		float aimPitch = MathUtil.xRotDegFromVec(toEyes);
		if (!dash) {
			stand.setYRot(aimYaw + 60.0F);
			stand.setXRot(aimPitch + 40.0F);
			stand.setYHeadRot(aimYaw + 60.0F);
		}
		float yaw0 = stand.getYRot();
		float pitch0 = stand.getXRot();
		float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
		helper.assertTrue(max > 0.0F && (dash || max < 40.0F), "setup: the " + standId + " turns by up to " + max + " degrees");
		float yaw = yaw0 + Mth.clamp(Mth.degreesDifference(yaw0, aimYaw), -max, max);
		float pitch = pitch0 + Mth.clamp(Mth.degreesDifference(pitch0, aimPitch), -max, max);
		helper.assertTrue(Math.abs(Mth.degreesDifference(yaw0, yaw)) + Math.abs(pitch - pitch0) > 1.0F,
				"setup: the " + standId + " already faces the cow");

		switch (row) {
			case "barrage" -> {
				action.setPhaseStart(ActionPhase.PERFORM);
				action.actionTick();
			}
			case "dash_attack" -> dashTargets(action, stand);
			// its strike comes at the end of its lunge
			case "heavy_charged" -> {
				action.actionPerformStart();
				action.actionPerformEnd();
			}
			default -> action.actionPerformStart();
		}
		String what = "a " + standId + "'s " + row + " (yaw " + yaw0 + ", pitch " + pitch0 + ", turning by up to " + max
				+ " degrees)";
		if (beyond) {
			helper.assertTrue(Float.isNaN(atHit[0]) && cow.getHealth() == cow.getMaxHealth(), "setup: " + what + " hit the cow");
		}
		else if (!dash) {
			helper.assertTrue(!Float.isNaN(atHit[0]), "setup: " + what + " did not hit the cow");
			helper.assertTrue(Math.abs(Mth.degreesDifference(atHit[0], yaw)) < EPS && Math.abs(atHit[1] - pitch) < EPS,
					"1.16 turned " + what + " toward the cow's eyes before the hit, to yaw " + yaw + ", pitch " + pitch
							+ "; it struck at yaw " + atHit[0] + ", pitch " + atHit[1]);
		}
		else {
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "setup: " + what + " did not hit the cow");
		}
		helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS
				&& Math.abs(Mth.degreesDifference(stand.getYHeadRot(), yaw)) < EPS, "1.16 turned " + what + " toward the eyes of a cow "
						+ (beyond ? "beyond its reach, which it missed," : "it struck") + " to yaw " + yaw + ", pitch " + pitch
						+ " and its head onto that yaw; it is at yaw " + stand.getYRot() + ", pitch " + stand.getXRot() + ", head "
						+ stand.getYHeadRot());
		LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
	}

	/*
	 * 1.16 StandEntity.punch set each dash strike's target as the task's (setTaskTarget), but StandEntityTask.keepTarget kept
	 * none for the dash: checkRangeAndTarget asked standKeepsTarget, false for SilverChariotDashAttack, and its
	 * checkStandTarget was always NEGATIVE. So the task held no target and StandEntityAction.rotateStand left a Stand that
	 * moves by itself as its strike turned it, wherever the entity it struck went next.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theDashHoldsNoTargetAfterItsStrikeAndKeepsItsStrikeTurnAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "silver_chariot", scene -> {
			StandEntity stand = scene.stand();
			Player user = scene.user();
			StandPower power = scene.power();
			power.setResolveLevel(power.getMaxResolveLevel());
			power.setStamina(power.getMaxStamina());
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.updatePosition(user);
			EntityActionType type = strikeType(helper, scene, "dash_attack");
			EntityActionInstance dash = type.createActionObj();
			type.initActionFromConfig(dash, helper.getLevel(), user, stand);
			dash.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(dash, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == dash && dash instanceof DashStrike && !stand.isFollowingUser(),
					"setup: the dash is not the Silver Chariot's action, or its Stand still follows its user");
			stand.setYRot(0.0F);
			stand.setXRot(0.0F);
			stand.setYHeadRot(0.0F);
			Cow cow = scene.cow(new Vec3(stand.getX() + 0.5D, stand.getEyeY() - 0.7D, stand.getZ() + 2.0D).subtract(user.position()));
			dashTargets(dash, stand);
			float yaw = stand.getYRot();
			float pitch = stand.getXRot();
			helper.assertTrue(cow.getHealth() < cow.getMaxHealth() && Math.abs(yaw) > EPS,
					"setup: the dash did not strike the cow, or did not turn toward it: yaw " + yaw);
			helper.assertTrue(dash.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
					"1.16's dash task held no target after its strike; it holds " + dash.getTaskTarget(helper.getLevel()));

			cow.moveTo(cow.getX() + 3.0D, cow.getY() + 1.0D, cow.getZ(), 0.0F, 0.0F);
			stand.updatePosition(user);
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < EPS && Math.abs(stand.getXRot() - pitch) < EPS,
					"1.16 left the dashing Stand as its strike turned it (yaw " + yaw + ", pitch " + pitch + ") once the cow it struck"
							+ " moved; it is at yaw " + stand.getYRot() + ", pitch " + stand.getXRot());
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
		});
		helper.succeed();
	}

	private static void noteStrike(float[] atHit, StandEntity stand) {
		if (Float.isNaN(atHit[0])) {
			atHit[0] = stand.getYRot();
			atHit[1] = stand.getXRot();
		}
	}

	private static EntityActionType strikeType(GameTestHelper helper, Scene scene, String name) {
		Ability ability = scene.power().getAbility(name);
		if (!(ability instanceof EntityActionType)) {
			scene.power().getCurTypeData()._setSkillUnlocked(name, true, false);
			ability = scene.power().getAbility(name);
		}
		helper.assertTrue(ability instanceof EntityActionType, "Missing registered " + name);
		return (EntityActionType) ability;
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

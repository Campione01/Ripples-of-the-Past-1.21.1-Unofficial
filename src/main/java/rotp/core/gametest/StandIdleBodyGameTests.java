package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.MathUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.defaultRotation gave a Stand its user's yaw, pitch and head and never its body. The body came from
 * StandEntity.tickHeadTurn: the yaw the Stand started the tick with while it had a task or was in its summon pose (on the
 * server until its first task), else 1.16's LivingEntity.tickHeadTurn from its own yaw and movement.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandIdleBodyGameTests {
	private static final float EPS = 1.0E-3F;
	private static final float VIEW_YAW = 0.0F;

	private StandIdleBodyGameTests() {}

	/*
	 * 1.16 LivingEntity.tickHeadTurn: a body within 50 degrees of the yaw stays where it is; beyond that it is held at most
	 * 75 behind and then closes a fifth of the gap (1.21's turn holds it at 50). Whatever the user's body does.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anIdleStandsBodyTrailsItsOwnYawByTheHeadTurnOf116(GameTestHelper helper) {
		// the body this many degrees behind the yaw, and where 1.16 left it
		float[][] rows = { { 40.0F, 40.0F }, { 60.0F, 48.0F }, { 100.0F, 60.0F } };
		for (float[] row : rows) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				StandEntity stand = scene.stand();
				endSummonPoseWithATask(helper, scene);
				float yaw = VIEW_YAW + row[0];
				StandTaskTargetOffsetGameTests.turn(scene.user(), yaw, yaw + 30.0F);
				restAt(stand, yaw, VIEW_YAW);
				stand.tick();
				helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, yaw - row[1])) < EPS, "1.16's head turn left"
						+ " the body of an idle hierophant_green " + row[0] + " degrees behind its yaw " + yaw + " at " + (yaw - row[1])
						+ ", whatever its user's body (" + (yaw + 30.0F) + "): it is at " + stand.yBodyRot);
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandPose.SUMMON: a Stand that has had no task yet took its yaw as its body on every tick of the server, as it
	 * did as it was summoned; its first task ended that.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandInItsSummonPoseTakesItsYawAsItsBodyAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			StandEntity stand = scene.stand();
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, stand.getYRot())) < EPS, "1.16 summoned a"
					+ " hierophant_green in its summon pose, its body on its yaw " + stand.getYRot() + ": the body is at "
					+ stand.yBodyRot);
			float yaw = VIEW_YAW + 40.0F;
			StandTaskTargetOffsetGameTests.turn(scene.user(), yaw, yaw + 30.0F);
			restAt(stand, yaw, VIEW_YAW);
			stand.tick();
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, yaw)) < EPS, "1.16 gave a hierophant_green that"
					+ " had had no task its yaw " + yaw + " as its body (the summon pose): it is at " + stand.yBodyRot);
		});
		helper.succeed();
	}

	/*
	 * 1.16 left the body where the task's last tick put it, on the yaw, and the head turn went on from there: no jump to
	 * the user's body as the task ended, and it trails the yaw once that is 50 degrees away.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandsBodyStaysWhereItsTaskLeftItAndThenTrailsItsYawAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			StandEntity stand = scene.stand();
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			actions.setAction(probeAction(helper, scene), scene.user(), SyncType.NO_SYNC);
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW + 80.0F);
			// as an action's tick left it 40 degrees off its user's look
			restAt(stand, VIEW_YAW + 40.0F, VIEW_YAW);
			stand.tick();
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, VIEW_YAW + 40.0F)) < EPS
					&& Math.abs(Mth.degreesDifference(stand.getYRot(), VIEW_YAW)) < EPS, "setup: the task left the body at "
							+ stand.yBodyRot + " and the yaw at " + stand.getYRot());
			actions.setAction(null, scene.user(), SyncType.NO_SYNC);
			stand.setOldPosAndRot();
			stand.tick();
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, VIEW_YAW + 40.0F)) < EPS, "1.16 left the body of a"
					+ " hierophant_green whose task ended where the task put it, at " + (VIEW_YAW + 40.0F) + ", 40 degrees off its"
					+ " yaw and not on its user's body " + (VIEW_YAW + 80.0F) + ": it is at " + stand.yBodyRot);
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW - 30.0F, VIEW_YAW + 80.0F);
			stand.setOldPosAndRot();
			stand.tick();
			stand.setOldPosAndRot();
			stand.tick();
			// the second tick starts on the new yaw, 70 degrees from the body, and closes a fifth of that
			helper.assertTrue(Math.abs(Mth.degreesDifference(stand.yBodyRot, VIEW_YAW + 26.0F)) < EPS, "1.16's head turn"
					+ " brought the body of a hierophant_green, 70 degrees off its new yaw " + (VIEW_YAW - 30.0F) + " after its task,"
					+ " to " + (VIEW_YAW + 26.0F) + ": it is at " + stand.yBodyRot);
		});
		helper.succeed();
	}

	/*
	 * The idle Stand's look at the entity its aim holds (a port addition kept with its 75-degree limit) turns it only while
	 * that entity lies within 75 degrees of its user's body, as when the idle Stand took its user's body yaw. Measured from
	 * the Stand's own body, which trails the yaw that look gave it, the Stand went on staring at a cow behind its user.
	 * Both bodies a Stand can have: on its yaw in the summon pose (the server before the first action), trailing it after
	 * (a client once the summon animation is over, the server after an action).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theIdleStandsLookAtItsAimedEntityIsLimitedByItsUsersBody(GameTestHelper helper) {
		// the user's view and body yaw once turned, and whether the Stand still looks at the cow
		float[][] rows = { { 180.0F, 180.0F, 0.0F }, { 150.0F, 100.0F, 1.0F } };
		for (boolean summonPose : new boolean[] { true, false }) {
			for (float[] row : rows) {
				StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
					StandEntity stand = scene.stand();
					if (!summonPose) {
						endSummonPoseWithATask(helper, scene);
					}
					String what = "an idle star_platinum " + (summonPose ? "in its summon pose" : "past its summon pose")
							+ " whose aim holds a cow, its user turned from the cow to view " + row[0] + " and body " + row[1];
					float off = 40.0F * Mth.DEG_TO_RAD;
					Cow cow = scene.cow(new Vec3(-3.0F * Mth.sin(off), 0.0D, 3.0F * Mth.cos(off)));
					LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));
					StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
					restAt(stand, VIEW_YAW, VIEW_YAW);
					stand.tick();
					float atCow = yawTo(stand, cow);
					helper.assertTrue(stand.getCurStandAction() == null && Math.abs(Mth.degreesDifference(stand.getYRot(), atCow)) < EPS
							&& Math.abs(Mth.degreesDifference(stand.getYRot(), VIEW_YAW)) > 10.0F, "setup: the idle star_platinum"
									+ " looks at the cow its user faces: yaw " + stand.getYRot() + ", the cow at " + atCow);
					StandTaskTargetOffsetGameTests.turn(scene.user(), row[0], row[1]);
					stand.setOldPosAndRot();
					stand.tick();
					atCow = yawTo(stand, cow);
					boolean withinBody = Math.abs(Mth.degreesDifference(row[1], atCow)) < 75.0F;
					if (row[2] > 0.0F) {
						helper.assertTrue(withinBody && Math.abs(Mth.degreesDifference(row[0], atCow)) > 75.0F, "setup: the cow"
								+ " at " + atCow + " is within 75 degrees of the user's body and not of the view");
						helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), atCow)) < EPS, what + " still looks at"
								+ " it, within 75 degrees of its user's body: yaw " + stand.getYRot() + ", the cow at " + atCow);
					}
					else {
						helper.assertTrue(!withinBody, "setup: the cow at " + atCow + " is within 75 degrees of the user's body");
						helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), row[0])) < EPS
								&& Math.abs(Mth.degreesDifference(stand.getYHeadRot(), row[0])) < EPS, what + " lets the cow"
										+ " go, more than 75 degrees from its user's body, and looks along its user's view: yaw "
										+ stand.getYRot() + ", head " + stand.getYHeadRot() + ", the cow at " + atCow);
					}
				});
			}
		}
		helper.succeed();
	}

	private static float yawTo(StandEntity stand, Cow cow) {
		return MathUtil.lookAnglesTowards(cow.getEyePosition(), stand, EntityAnchorArgument.Anchor.EYES).y;
	}

	// a Stand's task ended its summon pose (1.16 onSyncedDataUpdated, CURRENT_TASK)
	private static void endSummonPoseWithATask(GameTestHelper helper, Scene scene) {
		LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
		actions.setAction(probeAction(helper, scene), scene.user(), SyncType.NO_SYNC);
		actions.setAction(null, scene.user(), SyncType.NO_SYNC);
		helper.assertTrue(scene.stand().getCurStandAction() == null && scene.stand().isFollowingUser(),
				"setup: the hierophant_green is not idle after its task");
	}

	private static EntityActionInstance probeAction(GameTestHelper helper, Scene scene) {
		scene.stand().summonLockTicks = 0;
		return new BodyProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
				new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "idle_body_probe"))
				.initActionOnAbilityUse(helper.getLevel(), scene.user(), scene.stand(), null);
	}

	// the Stand at rest since the last tick, as the level marks it before ticking it, with this yaw and body
	private static void restAt(StandEntity stand, float yaw, float body) {
		stand.setYRot(yaw);
		stand.yBodyRot = body;
		stand.setDeltaMovement(Vec3.ZERO);
		stand.setOldPosAndRot();
	}

	private static final class BodyProbe extends StandEntityAbility {
		BodyProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, EntityActionInstance::new);
			setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		}
	}
}

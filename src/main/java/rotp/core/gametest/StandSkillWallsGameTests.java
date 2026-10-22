package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.entityaction.type.SpecialEntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Walls as in the original story (owner, 2026-10-15): a skill marked through {@link StandSkillWalls} picks and hits a
 * mob behind a block, as the 1.16 entity rays did; the block stops a skill that is not marked. Star Platinum's punch is
 * marked only inside these tests. Each test has its own batch: the mark is global while a batch runs, and the rays
 * reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandSkillWallsGameTests {
	private static final String PICK_BATCH = "stand_skill_walls_pick";
	private static final String MARKED_BATCH = "stand_skill_walls_marked";
	private static final String UNMARKED_BATCH = "stand_skill_walls_unmarked";
	private static final String SYNCED_AIM_BATCH = "stand_skill_walls_synced_aim";
	private static final String KEPT_TARGET_BATCH = "stand_skill_walls_kept_target";
	private static final String MANUAL_BATCH = "stand_skill_walls_manual";
	private static final String ATTACK_HIT_BATCH = "stand_skill_walls_attack_hit";
	private static final String ATTACK_STRICT_BATCH = "stand_skill_walls_attack_strict";
	private static final String ATTACK_WALL_BATCH = "stand_skill_walls_attack_wall";
	private static final String ATTACK_FAR_BATCH = "stand_skill_walls_attack_far";
	private static final String ATTACK_FAR_STRICT_BATCH = "stand_skill_walls_attack_far_strict";
	private static final String KEPT_WALL_BATCH = "stand_skill_walls_kept_wall";
	private static final String MANUAL_STAND_AIM_BATCH = "stand_skill_walls_manual_stand_aim";
	private static final String CHARGED_BATCH = "stand_skill_walls_charged";
	private static final String USER_OWN_ACTION_BATCH = "stand_skill_walls_user_own_action";
	private static final String CHARGED_LUNGE_BATCH = "stand_skill_walls_charged_lunge";
	private static final String FRONT_OFFSET_BATCH = "stand_skill_walls_front_offset";
	private static final String USER_STAND_MOVE_BATCH = "stand_skill_walls_user_stand_move";
	private static final short KEY = 173;

	@Nullable
	private static Scene active;

	private StandSkillWallsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20, batch = PICK_BATCH)
	public static void onlyAMarkedSkillPicksAMobBehindAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
		try {
			Cow cow = scene.cowBehindWall();
			EntityActionInstance punch = scene.action("punch");
			component.setAction(punch, scene.user, SyncType.NO_SYNC);
			for (boolean marked : new boolean[] { true, false }) {
				scene.mark(marked);
				scene.clearAims();
				ActionTarget target = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
				if (marked) {
					helper.assertTrue(target.getMainEntity() == cow,
							"A marked punch must pick the cow behind the wall; got " + target);
				}
				else {
					helper.assertTrue(target.getType() == ActionTarget.TargetType.BLOCK,
							"The wall must stop the unmarked punch; got " + target);
				}
			}
			helper.succeed();
		}
		finally {
			component.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = MARKED_BATCH)
	public static void markedPunchHitsAMobBehindAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mark(true);
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				EntityActionInstance punch = scene.press("punch");
				helper.assertTrue(punch instanceof StandEntityPunch && StandSkillWalls.isMarked(punch),
						"Fixture: the punch input started " + punch + ", which is not the marked punch");
			}
			return tick > 3 && cow.getHealth() < health;
		}, 30, () -> "The marked punch did not hit the cow behind the wall");
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = UNMARKED_BATCH)
	public static void unmarkedPunchIsStoppedByAWall(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mark(false);
		float health = cow.getHealth();
		EntityActionInstance[] punch = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.clearAims();
				punch[0] = scene.press("punch");
				helper.assertTrue(punch[0] instanceof StandEntityPunch && !StandSkillWalls.isMarked(punch[0]),
						"Fixture: the punch input started " + punch[0] + ", which is not the unmarked punch");
			}
			else if (tick > 3 && cow.getHealth() < health) {
				helper.fail("The unmarked punch hit the cow behind the wall");
			}
			else if (tick > 25) {
				helper.assertTrue(punch[0].punchedTarget != null
								&& punch[0].punchedTarget.getType() == ActionTarget.TargetType.BLOCK,
						"Fixture: the punch did not strike the wall: " + punch[0].punchedTarget);
				return true;
			}
			return false;
		}, Integer.MAX_VALUE, () -> "unreachable");
	}

	// a client picks its aims while no action runs, so what it sends stops at the wall; the marked punch looks again
	@GameTest(template = "empty", timeoutTicks = 60, batch = SYNCED_AIM_BATCH)
	public static void markedPunchFromTheSyncedAimsHitsAMobBehindAWall(GameTestHelper helper) {
		punchFromIdleAims(helper, false, false);
	}

	// the setting decides only the barrage: the punch keeps its press target either way
	@GameTest(template = "empty", timeoutTicks = 60, batch = KEPT_TARGET_BATCH)
	public static void markedPunchWithKeepAttackTargetHitsAMobBehindAWall(GameTestHelper helper) {
		punchFromIdleAims(helper, true, false);
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = MANUAL_BATCH)
	public static void markedPunchUnderManualControlHitsAMobBehindAWall(GameTestHelper helper) {
		punchFromIdleAims(helper, false, true);
	}

	private static void punchFromIdleAims(GameTestHelper helper, boolean keepAttackTarget, boolean manualControl) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mark(true);
		scene.settings().standAttackTargetLock = keepAttackTarget;
		if (manualControl) {
			scene.stand.setManuallyControlled(true);
			// the user looks away: only the Stand's own aim can find the cow
			scene.user.setYRot(180);
			scene.user.setYHeadRot(180);
		}
		String how = manualControl ? "under manual control" : keepAttackTarget ? "with keep attack target on" : "from the synced aims";
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				EntityActionInstance punch = scene.press("punch");
				helper.assertTrue(punch instanceof StandEntityPunch && StandSkillWalls.isMarked(punch),
						"Fixture: the punch input started " + punch + ", which is not the marked punch");
				// 1.16 kept a light punch's pressed target (StandEntityLightAttack.standKeepsTarget) whatever the setting
				helper.assertTrue(StandEntityPunchAbility.shouldRetainPunchTarget(scene.stand, punch.ability),
						"The punch " + how + " does not keep its press target; 1.16 kept a light punch's pressed target");
			}
			return tick > 3 && cow.getHealth() < health;
		}, 30, () -> "The marked punch " + how + " did not hit the cow behind the wall");
	}

	// a Stand whose attacks cross matter (owner, 2026-10-15): the user's aim decides between the mob behind and the wall
	@GameTest(template = "empty", timeoutTicks = 80, batch = ATTACK_HIT_BATCH)
	public static void markedStandBarrageHitsTheMobItsAimMeetsBehindAWall(GameTestHelper helper) {
		barrageAtAOneBlockWall(helper, true, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = ATTACK_STRICT_BATCH)
	public static void markedStandBarrageWithoutAimAssistHitsTheMobOnTheCrosshairBehindAWall(GameTestHelper helper) {
		barrageAtAOneBlockWall(helper, true, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = ATTACK_WALL_BATCH)
	public static void markedStandBarrageBreaksAWallWithNothingBehindIt(GameTestHelper helper) {
		barrageAtAOneBlockWall(helper, false, true);
	}

	private static void barrageAtAOneBlockWall(GameTestHelper helper, boolean cowBehind, boolean aimAssist) {
		Scene scene = new Scene(helper);
		BlockPos wall = scene.oneBlockWall();
		Cow cow = cowBehind ? scene.cow() : null;
		scene.markAttacks(true);
		scene.settings().standAimAssist = aimAssist;
		float health = cow != null ? cow.getHealth() : 0;
		int[] hitTick = new int[1];
		String aim = aimAssist ? "" : " without the aim assist";
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				EntityActionInstance barrage = scene.action("barrage");
				LivingComponentAction.getComponent(scene.stand).setAction(barrage, scene.user, SyncType.NO_SYNC);
				helper.assertTrue(barrage instanceof StandEntityBarrage && scene.stand.getCurStandAction() == barrage
						&& !StandSkillWalls.isMarked(barrage.ability.getAbilityId()),
						"Fixture: " + barrage + " is not Star Platinum's barrage, or its name is marked on its own");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			boolean wallStands = helper.getLevel().getBlockState(wall).is(Blocks.STONE);
			if (cow == null) {
				return !wallStands;
			}
			helper.assertTrue(wallStands, "The marked barrage" + aim + " broke the wall instead of hitting the cow behind it");
			if (hitTick[0] == 0 && cow.getHealth() < health) {
				hitTick[0] = tick;
			}
			return hitTick[0] > 0 && tick >= hitTick[0] + 10;
		}, 50, () -> cow == null ? "The marked barrage did not break the wall with nothing behind it"
				: "The marked barrage" + aim + " did not hit the cow behind the wall");
	}

	// the aim meets a mob behind the wall that the Stand cannot reach: the wall the user aims at is struck (owner, 2026-10-15)
	@GameTest(template = "empty", timeoutTicks = 80, batch = ATTACK_FAR_BATCH)
	public static void markedStandBarrageBreaksTheWallItsAimMeetsWhenTheMobBehindIsOutOfReach(GameTestHelper helper) {
		barrageAtAWallWithAMobOutOfReachBehind(helper, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = ATTACK_FAR_STRICT_BATCH)
	public static void markedStandBarrageWithoutAimAssistBreaksTheWallWhenTheMobBehindIsOutOfReach(GameTestHelper helper) {
		barrageAtAWallWithAMobOutOfReachBehind(helper, false);
	}

	private static void barrageAtAWallWithAMobOutOfReachBehind(GameTestHelper helper, boolean aimAssist) {
		Scene scene = new Scene(helper);
		scene.settings().standAimAssist = aimAssist;
		scene.lookStraightAhead();
		BlockPos wall = scene.oneBlockWall();
		Cow cow = scene.cowOutOfReachBehindTheWall();
		scene.markAttacks(true);
		float health = cow.getHealth();
		String aim = aimAssist ? "" : " without the aim assist";
		String[] chargedMiss = new String[1];
		scene.run(tick -> {
			if (tick == 3) {
				// the charged heavy punch's own fallback aim at the end of its lunge, read before the barrage breaks the wall
				LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
				EntityActionInstance charged = scene.action("heavy_charged");
				component.setAction(charged, scene.user, SyncType.NO_SYNC);
				ActionTarget chargedAim = StandEntityHeavyPunchChargedAbility.userAimTarget(scene.stand, charged, false);
				component.setAction(null, scene.user, SyncType.NO_SYNC);
				if (chargedAim.getType() != ActionTarget.TargetType.BLOCK || !wall.equals(chargedAim.getBlockPos())) {
					chargedMiss[0] = "The marked charged heavy punch" + aim + " aimed at " + chargedAim
							+ " instead of the wall in front of a cow out of its reach";
				}
				scene.syncIdleAims();
				EntityActionInstance barrage = scene.action("barrage");
				component.setAction(barrage, scene.user, SyncType.NO_SYNC);
				helper.assertTrue(barrage instanceof StandEntityBarrage && scene.stand.getCurStandAction() == barrage
						&& StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, barrage),
						"Fixture: " + barrage + " is not Star Platinum's directional barrage");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(cow.getHealth() >= health, "The marked barrage" + aim + " hurt a cow out of the Stand's reach");
			boolean broken = !helper.getLevel().getBlockState(wall).is(Blocks.STONE);
			helper.assertTrue(!broken || chargedMiss[0] == null, String.valueOf(chargedMiss[0]));
			return broken;
		}, 50, () -> "The marked barrage" + aim + " did not break the wall it aimed at, with a cow out of its reach behind it"
				+ (chargedMiss[0] != null ? "; " + chargedMiss[0] : ""));
	}

	// pressed with nothing behind the wall, the punch keeps the wall as 1.16 kept a pressed block, the keep attack target
	// setting off; a mob that comes behind it before the swing is hit through it
	@GameTest(template = "empty", timeoutTicks = 60, batch = KEPT_WALL_BATCH)
	public static void markedPunchKeepingAWallTargetHitsAMobThatComesBehindIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.wall(new BlockPos(1, 2, 2));
		scene.wall(new BlockPos(1, 3, 2));
		scene.mark(true);
		Cow[] cow = new Cow[1];
		float[] health = new float[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				EntityActionInstance punch = scene.press("punch");
				helper.assertTrue(punch instanceof StandEntityPunch && StandSkillWalls.isMarked(punch)
						&& StandEntityPunchAbility.shouldRetainPunchTarget(scene.stand, punch.ability),
						"Fixture: the punch input started " + punch + ", which is not the marked punch keeping its target");
				helper.assertTrue(punch.punchedTarget == null && scene.stand.getCurStandAction() == punch,
						"Fixture: the punch swung before the cow came");
				cow[0] = scene.cow();
				health[0] = cow[0].getHealth();
			}
			return tick > 3 && cow[0].getHealth() < health[0];
		}, 30, () -> "The marked punch kept the wall it was pressed at and struck it, not the cow that came behind it");
	}

	// manual control: the press found nothing, and the Stand's aim a client sent afterwards is the wall
	@GameTest(template = "empty", timeoutTicks = 60, batch = MANUAL_STAND_AIM_BATCH)
	public static void markedPunchUnderManualControlLooksThroughTheWallInTheStandsAim(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Cow cow = scene.cowBehindWall();
		scene.mark(true);
		scene.stand.setManuallyControlled(true);
		scene.user.setYRot(180);
		scene.user.setYHeadRot(180);
		float health = cow.getHealth();
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				ActionTarget wall = LivingComponentAction.getComponent(scene.stand).entityAim.getTarget();
				scene.clearAims();
				EntityActionInstance punch = scene.press("punch");
				helper.assertTrue(punch instanceof StandEntityPunch && StandSkillWalls.isMarked(punch)
						&& punch.punchedTarget == null, "Fixture: the punch input started " + punch
						+ ", which is not the marked punch, or it swung at once");
				LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(wall);
			}
			return tick > 3 && cow.getHealth() < health;
		}, 30, () -> "The marked punch under manual control struck the wall in the Stand's aim, not the cow behind it");
	}

	// the charged heavy punch takes the Stand's synced aim at its perform start, here the wall; the user stands against
	// the wall, so that the cow behind it is within the reach of the Stand at its idle place, where it starts to punch
	@GameTest(template = "empty", timeoutTicks = 100, batch = CHARGED_BATCH)
	public static void markedChargedHeavyPunchLooksThroughTheWallInTheStandsAim(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.wall(new BlockPos(1, 2, 2));
		scene.wall(new BlockPos(1, 3, 2));
		Vec3 cowPos = scene.origin.add(0, 0, 2.0);
		scene.user.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 0.2, 0, 0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cowPos.add(0, 0.7, 0));
		Cow cow = scene.cow(cowPos);
		scene.markAttacks(true);
		EntityActionInstance[] charged = new EntityActionInstance[1];
		Boolean[] reachAtStart = new Boolean[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				charged[0] = scene.action("heavy_charged");
				LivingComponentAction.getComponent(scene.stand).setAction(charged[0], scene.user, SyncType.NO_SYNC);
				helper.assertTrue(charged[0] instanceof StandEntityChargedHeavy && scene.stand.getCurStandAction() == charged[0]
						&& StandSkillWalls.isMarked(charged[0]), "Fixture: " + charged[0] + " is not the marked charged heavy punch");
				charged[0].onKeyRelease(scene.user);
				return false;
			}
			if (tick < 3 || charged[0].punchedTarget == null) {
				if (tick > 3 && charged[0].getPhase() == ActionPhase.PERFORM && reachAtStart[0] == null) {
					reachAtStart[0] = !StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(cow)).isEmpty(
							helper.getLevel());
				}
				return false;
			}
			helper.assertTrue(Boolean.TRUE.equals(reachAtStart[0]), "Fixture: the Stand could not reach the cow when it"
					+ " started to punch; Stand at " + scene.stand.position() + ", cow at " + cow.position());
			helper.assertTrue(charged[0].punchedTarget.getMainEntity() == cow, "The marked charged heavy punch struck "
					+ charged[0].punchedTarget + ", the wall in the Stand's aim, not the cow behind it");
			return true;
		}, 80, () -> "The marked charged heavy punch did not strike");
	}

	// the user's aim decides (owner, 2026-10-15) as in the open: a mob behind the wall out of the Stand's reach when the
	// charged heavy punch starts is no reason to strike the wall, the lunge brings it into reach and the Stand aims again
	@GameTest(template = "empty", timeoutTicks = 100, batch = CHARGED_LUNGE_BATCH)
	public static void markedChargedHeavyPunchLungesAtAMobBehindAWallOutOfReachAtItsStart(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		scene.lookStraightAhead();
		scene.wall(new BlockPos(1, 2, 3));
		scene.wall(new BlockPos(1, 3, 3));
		Cow cow = scene.cow(scene.origin.add(0, 0.8, 3.6));
		scene.markAttacks(true);
		EntityActionInstance[] charged = new EntityActionInstance[1];
		Boolean[] reachAtStart = new Boolean[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.syncIdleAims();
				charged[0] = scene.action("heavy_charged");
				LivingComponentAction.getComponent(scene.stand).setAction(charged[0], scene.user, SyncType.NO_SYNC);
				helper.assertTrue(charged[0] instanceof StandEntityChargedHeavy && scene.stand.getCurStandAction() == charged[0]
						&& StandSkillWalls.isMarked(charged[0]), "Fixture: " + charged[0] + " is not the marked charged heavy punch");
				charged[0].onKeyRelease(scene.user);
				return false;
			}
			if (tick < 3 || charged[0].punchedTarget == null) {
				if (tick > 3 && charged[0].getPhase() == ActionPhase.PERFORM && reachAtStart[0] == null) {
					reachAtStart[0] = !StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(cow)).isEmpty(
							helper.getLevel());
				}
				return false;
			}
			helper.assertTrue(Boolean.FALSE.equals(reachAtStart[0]), "Fixture: the Stand could reach the cow when it started"
					+ " to punch; Stand at " + scene.stand.position() + ", cow at " + cow.position());
			helper.assertTrue(charged[0].punchedTarget.getMainEntity() == cow, "The marked charged heavy punch struck "
					+ charged[0].punchedTarget + " instead of lunging at the cow behind the wall, out of the Stand's reach"
					+ " only at its start; Stand at " + scene.stand.position());
			return true;
		}, 80, () -> "The marked charged heavy punch did not strike");
	}

	// the mark of the Stand's action reaches the user's rays only while the user runs no action of its own; the Stand's
	// marked punch keeps passing the wall either way
	@GameTest(template = "empty", timeoutTicks = 20, batch = USER_OWN_ACTION_BATCH)
	public static void aRayForTheUsersOwnActionStopsAtAWallWhileItsStandRunsAMarkedPunch(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		LivingComponentAction userActions = LivingComponentAction.getComponent(scene.user);
		try {
			Cow cow = scene.cowBehindWall();
			scene.mark(true);
			scene.clearAims();
			standActions.setAction(scene.action("punch"), scene.user, SyncType.NO_SYNC);
			ActionTarget idleUserRay = scene.userRay();
			helper.assertTrue(idleUserRay.getMainEntity() == cow, "A ray from the eyes of a user with no action of its own"
					+ " must follow its Stand's marked punch through the wall; got " + idleUserRay);
			EntityActionInstance own = new SpecialEntityActionType(null, JojoMod.resLoc("test_user_own_action")) {}
					.createActionObj();
			userActions.setAction(own, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.user) == own,
					"Fixture: the user's own action did not start");
			ActionTarget ownRay = scene.userRay();
			helper.assertTrue(ownRay.getType() == ActionTarget.TargetType.BLOCK, "A ray the user casts for an action of its"
					+ " own passed the wall during its Stand's marked punch; got " + ownRay);
			ActionTarget punchTarget = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			helper.assertTrue(punchTarget.getMainEntity() == cow, "The Stand's marked punch stopped at the wall while its user"
					+ " ran an action of its own; got " + punchTarget);
			helper.succeed();
		}
		finally {
			userActions.setAction(null, scene.user, SyncType.NO_SYNC);
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	@AfterBatch(batch = USER_OWN_ACTION_BATCH)
	public static void releaseUserOwnActionBatch(ServerLevel level) {
		releaseActive();
	}

	// a Stand move the user performs itself sits in its Stand-power slot (K5-48), beside an empty own slot: it is an
	// action of the user's own (D-core-89 for the rest), and a port of a 1.16 StandAction there answers for the Stand
	@GameTest(template = "empty", timeoutTicks = 20, batch = USER_STAND_MOVE_BATCH)
	public static void aStandMoveItsUserPerformsAnswersForTheUsersRaysAsAnActionOfItsOwn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		LivingComponentAction userStandPowerSlot = LivingComponentAction.getComponent(scene.user, PowerClass.STAND);
		AbilityId moveId = new AbilityId(PowerClass.STAND, scene.markedId.powerTypeId(), "test_user_stand_move");
		AbilityId portId = new AbilityId(PowerClass.STAND, scene.markedId.powerTypeId(), "test_stand_action_port");
		try {
			Cow cow = scene.cowBehindWall();
			scene.clearAims();
			List<String> wrong = new ArrayList<>();
			scene.mark(true);
			standActions.setAction(scene.action("punch"), scene.user, SyncType.NO_SYNC);
			EntityActionInstance move = new TestStandMove(moveId, false).createActionObj();
			userStandPowerSlot.setAction(move, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.user, PowerClass.STAND) == move
					&& LivingComponentAction.getCurEntityAction(scene.user) == null && move.getPerformer() == scene.user,
					"Fixture: the user's Stand move is not in its Stand-power slot, performed by the user");
			ActionTarget ray = scene.userRay();
			if (ray.getType() != ActionTarget.TargetType.BLOCK || !StandSkillWalls.wallsHideEntities(scene.user)) {
				wrong.add("a ray for the user's unmarked Stand move met " + ray + " during its Stand's marked punch,"
						+ " where its own action's answer stops it at the wall");
			}
			ActionTarget punchTarget = StandEntityPunchAbility.getFreshPunchTarget(scene.stand, ActionTarget.EMPTY);
			if (punchTarget.getMainEntity() != cow) {
				wrong.add("the Stand's marked punch picked " + punchTarget + " during its user's Stand move");
			}

			scene.mark(false);
			StandSkillWalls.markThroughWalls(moveId.powerTypeId(), moveId.nameInMoveset());
			ray = scene.userRay();
			if (ray.getMainEntity() != cow || StandSkillWalls.wallsHideEntities(scene.user)) {
				wrong.add("a ray for the user's marked Stand move met " + ray + " during its Stand's unmarked punch,"
						+ " where its own mark passes the wall");
			}
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
			if (!StandSkillWalls.wallsHideEntities(scene.stand)) {
				wrong.add("the idle Stand's rays passed walls during its user's marked Stand move, which the Stand does not perform");
			}

			userStandPowerSlot.setAction(null, scene.user, SyncType.NO_SYNC);
			StandSkillWalls.unmarkThroughWalls(moveId.powerTypeId(), moveId.nameInMoveset());
			StandSkillWalls.markThroughWalls(portId.powerTypeId(), portId.nameInMoveset());
			EntityActionInstance port = new TestStandMove(portId, true).createActionObj();
			userStandPowerSlot.setAction(port, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.user, PowerClass.STAND) == port
					&& scene.stand.getCurStandAction() == null && port.getPerformer() == scene.stand,
					"Fixture: the marked port is not in the user's Stand-power slot, performed by the Stand, beside its empty slot");
			ray = scene.userRay();
			if (ray.getMainEntity() != cow || StandSkillWalls.wallsHideEntities(scene.stand)) {
				wrong.add("during a marked port its Stand performs from its user's Stand-power slot, the user's ray met " + ray
						+ " and the Stand's rays stop at walls " + StandSkillWalls.wallsHideEntities(scene.stand)
						+ ", where the Stand's action passes them");
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		}
		finally {
			StandSkillWalls.unmarkThroughWalls(moveId.powerTypeId(), moveId.nameInMoveset());
			StandSkillWalls.unmarkThroughWalls(portId.powerTypeId(), portId.nameInMoveset());
			userStandPowerSlot.setAction(null, scene.user, SyncType.NO_SYNC);
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	@AfterBatch(batch = USER_STAND_MOVE_BATCH)
	public static void releaseUserStandMoveBatch(ServerLevel level) {
		releaseActive();
	}

	// a Stand-power move its user performs itself, or, as a port of a 1.16 StandAction, its summoned Stand from there
	private static final class TestStandMove extends EntityActionAbility {
		private final boolean port;

		TestStandMove(AbilityId abilityId, boolean port) {
			super(ModStandAbilities.PUNCH.get(), abilityId, EntityActionInstance::new);
			this.port = port;
			setDefaultPhaseLength(ActionPhase.PERFORM, 2);
		}

		@Override
		public boolean performsInUsersStandPowerSlot() {
			return port;
		}
	}

	@AfterBatch(batch = CHARGED_LUNGE_BATCH)
	public static void releaseChargedLungeBatch(ServerLevel level) {
		releaseActive();
	}

	// where the Stand stands for a marked punch pressed at nothing: its ray from the user's eyes follows the Stand's action
	// like the punch's other rays, also while the user runs an action of its own
	@GameTest(template = "empty", timeoutTicks = 20, batch = FRONT_OFFSET_BATCH)
	public static void aMarkedPunchStandsOffFromTheMobBehindAWallWhileItsUserRunsAnActionOfItsOwn(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		LivingComponentAction userActions = LivingComponentAction.getComponent(scene.user);
		try {
			scene.cowBehindWall();
			scene.mark(true);
			scene.clearAims();
			EntityActionInstance punch = scene.action("punch");
			standActions.setAction(punch, scene.user, SyncType.NO_SYNC);
			EntityActionInstance own = new SpecialEntityActionType(null, JojoMod.resLoc("test_user_own_action_offset")) {}
					.createActionObj();
			userActions.setAction(own, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(LivingComponentAction.getCurEntityAction(scene.user) == own
					&& scene.userRay().getType() == ActionTarget.TargetType.BLOCK,
					"Fixture: the user's own action did not start, or a ray for it passes the wall");
			// no lower clamp, so that the wall (no distance) and the cow behind it (0.3 blocks) give different distances
			punch.setStandFrontOffsetFromTarget(scene.stand, ActionTarget.EMPTY, 0, 3);
			Vec3 offset = punch.getSyncedStandOffset();
			helper.assertTrue(offset != null && offset.z > 0.1, "The marked punch pressed at nothing set the Stand's"
					+ " distance from the wall, not from the cow behind it, while its user ran an action of its own: " + offset);
			helper.succeed();
		}
		finally {
			userActions.setAction(null, scene.user, SyncType.NO_SYNC);
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
	}

	@AfterBatch(batch = FRONT_OFFSET_BATCH)
	public static void releaseFrontOffsetBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = KEPT_WALL_BATCH)
	public static void releaseKeptWallBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = MANUAL_STAND_AIM_BATCH)
	public static void releaseManualStandAimBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = CHARGED_BATCH)
	public static void releaseChargedBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = ATTACK_FAR_BATCH)
	public static void releaseAttackFarBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = ATTACK_FAR_STRICT_BATCH)
	public static void releaseAttackFarStrictBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = PICK_BATCH)
	public static void releasePickBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = ATTACK_HIT_BATCH)
	public static void releaseAttackHitBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = ATTACK_STRICT_BATCH)
	public static void releaseAttackStrictBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = ATTACK_WALL_BATCH)
	public static void releaseAttackWallBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = SYNCED_AIM_BATCH)
	public static void releaseSyncedAimBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = KEPT_TARGET_BATCH)
	public static void releaseKeptTargetBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = MANUAL_BATCH)
	public static void releaseManualBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = MARKED_BATCH)
	public static void releaseMarkedBatch(ServerLevel level) {
		releaseActive();
	}

	@AfterBatch(batch = UNMARKED_BATCH)
	public static void releaseUnmarkedBatch(ServerLevel level) {
		releaseActive();
	}

	private static void releaseActive() {
		if (active != null) {
			active.close();
		}
	}

	static ModConfigSpec.ConfigValue<String> spec(String name) {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", name));
	}

	private interface Step {
		boolean run(int tick);
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	private static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;

		final List<Entity> entities = new ArrayList<>();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		AbilityId markedId;
		int ticks;
		boolean finished;
		boolean closed;

		Scene(GameTestHelper helper) {
			helper.assertTrue(active == null, "Fixture: overlapping wall setting scenes");
			active = this;
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			// looking down at the middle of a cow 2.2 blocks ahead
			user.moveTo(origin.x, origin.y, origin.z, 0, 22.7F);
			user.setYHeadRot(0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Star Platinum entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 22.7F);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = false;
			Ability punch = power.getAbility("punch");
			helper.assertTrue(punch != null && punch.getAbilityId().powerTypeId() != null,
					"Fixture: Star Platinum's punch has no moveset id");
			markedId = punch.getAbilityId();
		}

		// two bedrock blocks from half a block to a block and a half ahead, the cow behind them on the line of sight
		Cow cowBehindWall() {
			wall(new BlockPos(1, 2, 2));
			wall(new BlockPos(1, 3, 2));
			return cow();
		}

		// one stone block at eye level, half a block ahead, that the crosshair meets first; the Stand can break it
		BlockPos oneBlockWall() {
			BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 2));
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
			ActionTarget crosshair = StandEntityPunchAbility.crosshairPick(stand, user, 1.0F, entity -> false);
			helper.assertTrue(crosshair.getType() == ActionTarget.TargetType.BLOCK && pos.equals(crosshair.getBlockPos()),
					"Fixture: the crosshair does not meet the wall first: " + crosshair);
			helper.assertTrue(StandEntityPunch.canStandBreakBlockByStrength(stand, Blocks.STONE.defaultBlockState(),
					helper.getLevel(), pos) && StandEntityPunch.canStandBreakBlock(helper.getLevel(), pos,
					Blocks.STONE.defaultBlockState(), stand), "Fixture: Star Platinum cannot break the stone wall");
			return pos;
		}

		// level, along +z: the one-block wall is then at eye level half a block ahead
		void lookStraightAhead() {
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setYHeadRot(0);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
		}

		// on the level crosshair 4 blocks from the user's eyes, behind the one-block wall: within the user's aim reach,
		// out of the Stand's
		Cow cowOutOfReachBehindTheWall() {
			Cow cow = cow(origin.add(0, 0.8, 4.45));
			Vec3 eyes = user.getEyePosition();
			helper.assertTrue(cow.getBoundingBox().distanceToSqr(eyes) < Math.pow(StandEntityPunchAbility.userAimReach(user, stand), 2),
					"Fixture: the cow is beyond the user's aim reach");
			helper.assertTrue(StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow)).isEmpty(helper.getLevel()),
					"Fixture: Star Platinum can reach the cow behind the wall");
			return cow;
		}

		Cow cow() {
			return cow(origin.add(0, 0, 2.2));
		}

		Cow cow(Vec3 pos) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			Vec3 eyes = user.getEyePosition();
			helper.assertTrue(cow.getBoundingBox().clip(eyes, eyes.add(user.getViewVector(1).scale(5))).isPresent(),
					"Fixture: the cow is not on the crosshair");
			return cow;
		}

		void wall(BlockPos relative) {
			BlockPos pos = helper.absolutePos(relative);
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
		}

		void markAttacks(boolean marked) {
			if (marked) {
				StandSkillWalls.markAttacksThroughWalls(markedId.powerTypeId());
			}
			else {
				StandSkillWalls.unmarkAttacksThroughWalls(markedId.powerTypeId());
			}
		}

		void mark(boolean marked) {
			if (marked) {
				StandSkillWalls.markThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
			}
			else {
				StandSkillWalls.unmarkThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
			}
		}

		EntityActionInstance action(String name) {
			Ability ability = power.getAbility(name);
			helper.assertTrue(ability instanceof EntityActionType, "Fixture: no " + name + " action");
			return ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		}

		PlayerClientBroadcastedSettings settings() {
			return user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
		}

		// what an idle client sends (ClientsideAim): its crosshair target, and the Stand's aim from the user or, under
		// manual control, where the Stand is the camera, both from the Stand's eyes; all picked with no action running
		void syncIdleAims() {
			helper.assertTrue(stand.getCurStandAction() == null, "Fixture: the Stand is busy with " + stand.getCurStandAction());
			Predicate<Entity> aimable = entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity);
			ActionTarget crosshair;
			ActionTarget standAim;
			if (stand.isManuallyControlled()) {
				standAim = HitResultUtil.clip(stand.getEyePosition(), stand.getLookAngle(),
						stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
						stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE),
						stand.level(), aimable, stand, stand.getPrecision());
				crosshair = standAim;
			}
			else {
				crosshair = StandEntityPunchAbility.crosshairPick(stand, user, 1.0F,
						entity -> entity != stand && !entity.isSpectator() && entity.isPickable());
				standAim = StandEntityPunchAbility.userStandAim(stand, user, crosshair, aimable);
			}
			helper.assertTrue(crosshair.getType() == ActionTarget.TargetType.BLOCK
					&& standAim.getType() == ActionTarget.TargetType.BLOCK,
					"Fixture: an idle client's aims must stop at the wall; crosshair " + crosshair + ", Stand aim " + standAim);
			LivingComponentAction.getComponent(user).entityAim.setTarget(crosshair);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(standAim);
		}

		// a ray from the user's eyes through the helper that asks StandSkillWalls for the user
		ActionTarget userRay() {
			return HitResultUtil.clip(user.getEyePosition(), user.getLookAngle(), 5, 5, helper.getLevel(),
					entity -> entity instanceof Cow, user, 0);
		}

		void clearAims() {
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
		}

		EntityActionInstance press(String name) {
			Ability base = power.getAbility(name);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"The " + name + " input was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, buffer,
					base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The " + name + " key press failed");
			return held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		void run(Step step, int lastTick, java.util.function.Supplier<String> timeoutMessage) {
			helper.onEachTick(() -> {
				if (finished) return;
				try {
					if (step.run(++ticks)) {
						finished = true;
						close();
						helper.succeed();
					}
					else if (ticks >= lastTick) {
						finished = true;
						close();
						helper.fail(timeoutMessage.get());
					}
				}
				catch (RuntimeException | Error error) {
					finished = true;
					close();
					throw error;
				}
			});
		}

		void close() {
			if (closed) return;
			closed = true;
			if (markedId != null) {
				StandSkillWalls.unmarkThroughWalls(markedId.powerTypeId(), markedId.nameInMoveset());
				StandSkillWalls.unmarkAttacksThroughWalls(markedId.powerTypeId());
			}
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			placedBlocks.forEach(placed -> helper.getLevel().setBlockAndUpdate(placed.pos(), placed.previous()));
			placedBlocks.clear();
			if (active == this) {
				active = null;
			}
		}
	}
}

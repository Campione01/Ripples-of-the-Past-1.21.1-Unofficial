package rotp.core.gametest;

import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Attacks of a manually controlled Stand, and of one that aims on its own (StandEntity.aimsWithoutUser), take their target
 * from the Stand's own aim and eyes, never from its user's crosshair (1.16 StandEntity.aimWithThisOrUser skipped the user
 * there and cast the precision ray from the Stand, precisionRayTrace(this, getAimDistance)). Scenes are those of
 * {@link StandAttackAimRulesGameTests}. Each test has its own batch: the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualControlAimGameTests {
	private static final String BATCH = "stand_manual_control_aim_";

	@Nullable
	private static ResourceLocation markedStandType;

	private StandManualControlAimGameTests() {}

	private interface Step {
		boolean run(int tick);
	}

	private static void run(StandAttackAimRulesGameTests.Scene scene, Step step, int lastTick, Supplier<String> timeoutMessage) {
		run(scene, () -> {}, step, lastTick, timeoutMessage);
	}

	// Scene.run takes a private step type of its own class
	private static void run(StandAttackAimRulesGameTests.Scene scene, Runnable cleanup, Step step, int lastTick,
			Supplier<String> timeoutMessage) {
		scene.helper.onEachTick(() -> {
			if (scene.finished) return;
			try {
				if (step.run(++scene.ticks)) {
					scene.finished = true;
					cleanup.run();
					scene.close();
					scene.helper.succeed();
				}
				else if (scene.ticks >= lastTick) {
					scene.finished = true;
					cleanup.run();
					scene.close();
					scene.helper.fail(timeoutMessage.get());
				}
			}
			catch (RuntimeException | Error error) {
				scene.finished = true;
				cleanup.run();
				scene.close();
				throw error;
			}
		});
	}

	// The heavy punch keeps only an entity (1.16 StandEntityHeavyAttack.standKeepsTarget). The Stand's synced aim is a stone
	// block in its reach, the aim a client sent before a cow stepped beside it: the precision ray from the Stand's eyes takes
	// the cow within its boxes, and the stone is neither saved at the press nor struck.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "heavy")
	public static void manuallyControlledHeavyPunchDropsTheStoneInTheStandsAimForTheMobInItsPrecisionBoxesAsIn116(
			GameTestHelper helper) {
		StandAttackAimRulesGameTests.Scene scene = new StandAttackAimRulesGameTests.Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		scene.stand.setManuallyControlled(true);
		helper.assertTrue(scene.stand.isManuallyControlled() && scene.stand.aimsWithoutUser(),
				"Fixture: the Stand is not manually controlled");
		// the user looks away: only the Stand's own aim and eyes can find the stone or the cow
		scene.user.setYRot(180);
		scene.user.setYHeadRot(180);
		// the near edge of the stone's top, so that it is within the Stand's reach from its eyes
		scene.stand.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1).add(0, 0, -0.4));
		ActionTarget stone = new ActionTarget(ground, Direction.UP);
		Predicate<Entity> canHit = entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity);
		ActionTarget crosshair = HitResultUtil.clip(scene.stand.getEyePosition(), scene.stand.getLookAngle(),
				scene.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
				scene.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), helper.getLevel(), canHit, scene.stand, 0);
		helper.assertTrue(crosshair.getType() == ActionTarget.TargetType.BLOCK && ground.equals(crosshair.getBlockPos()),
				"Fixture: the Stand's crosshair does not meet the ground at " + ground + ": " + crosshair);
		helper.assertTrue(!StandEntityPunchAbility.validatePunchTarget(scene.stand, stone).isEmpty(helper.getLevel()),
				"Fixture: the stone is out of the Stand's reach");
		ActionTarget precision = StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1.0F, canHit,
				CrosshairRule.ENTITY, true);
		helper.assertTrue(precision.getMainEntity() == cow, "Fixture: the precision ray from the Stand's eyes does not take"
				+ " the cow before the ground: " + precision);
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		run(scene, tick -> {
			if (tick == 3) {
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(stone, helper.getLevel());
				action[0] = scene.press("heavy_punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityHeavyPunch, "Fixture: the heavy punch input started " + action[0]);
				ActionTarget saved = StandAttackAimRulesGameTests.savedTarget(action[0]);
				helper.assertTrue((saved == null || saved.getType() != ActionTarget.TargetType.BLOCK)
						&& (action[0].standRotationTarget == null
								|| action[0].standRotationTarget.getType() != ActionTarget.TargetType.BLOCK),
						"The manually controlled heavy punch pressed at the stone in the Stand's aim saved " + saved
								+ " and turns the Stand to " + action[0].standRotationTarget + "; 1.16 kept no block for a heavy punch");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			helper.assertTrue((struck == null || struck.getType() != ActionTarget.TargetType.BLOCK) && scene.isStone(ground),
					"The manually controlled heavy punch struck " + struck + " instead of the cow in the precision boxes of the"
							+ " Stand's eyes, which 1.16's precision ray took before the stone in the Stand's aim");
			return cow.getHealth() < health && tick > 15;
		}, 60, () -> "The manually controlled heavy punch never hit the cow in the precision boxes of the Stand's eyes");
	}

	// The charged heavy punch of a Stand marked through walls, the aim assist off, its aim a wall in its reach with a mob
	// behind it in its reach: the mob is struck through the wall (the owner, 2026-10-15), not the wall. Under manual control
	// the Stand does not lunge by itself (it does not follow its user's offset), and its ray reaches only as far as the Stand
	// does (1.16 getAimDistance), so it never meets a mob beyond its reach: the lunge flag of the perform start does not
	// matter here, see the self-aiming Stand below.
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH + "charged_reach")
	public static void manuallyControlledMarkedChargedHeavyPunchWithoutAimAssistStrikesTheMobBehindTheWallInReach(
			GameTestHelper helper) {
		StandAttackAimRulesGameTests.Scene scene = new StandAttackAimRulesGameTests.Scene(helper, false);
		BlockPos wallTop = helper.absolutePos(new BlockPos(1, 3, 2));
		scene.block(helper.absolutePos(new BlockPos(1, 2, 2)), Blocks.BEDROCK.defaultBlockState());
		scene.block(wallTop, Blocks.BEDROCK.defaultBlockState());
		Cow cow = scene.cow(0, 2.2);
		cow.moveTo(scene.origin.x, scene.origin.y + 0.8, scene.origin.z + 2.2, 0, 0);
		markedStandType = scene.power.getAbility("punch").getAbilityId().powerTypeId();
		StandSkillWalls.markAttacksThroughWalls(markedStandType);
		scene.stand.setManuallyControlled(true);
		helper.assertTrue(scene.stand.isManuallyControlled() && scene.stand.aimsWithoutUser(),
				"Fixture: the Stand is not manually controlled");
		// the user looks away: only the Stand's own aim and eyes can find the wall or the cow
		scene.user.setYRot(180);
		scene.user.setYHeadRot(180);
		helper.assertTrue(!StandEntityPunchAbility.validatePunchTarget(scene.stand, new ActionTarget(cow)).isEmpty(helper.getLevel()),
				"Fixture: the Stand cannot reach the cow");
		EntityActionInstance[] charged = new EntityActionInstance[1];
		run(scene, StandManualControlAimGameTests::unmark, tick -> {
			if (tick == 3) {
				// the Stand aim a client sends under manual control without the aim assist: its own crosshair, a wall stopping it
				ActionTarget standAim = HitResultUtil.clip(scene.stand.getEyePosition(), scene.stand.getLookAngle(),
						scene.stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),
						scene.stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), helper.getLevel(),
						entity -> StandEntityAbility.canDefaultTargetEntityForAiming(scene.stand, entity), scene.stand, 0, true);
				helper.assertTrue(standAim.getType() == ActionTarget.TargetType.BLOCK && wallTop.equals(standAim.getBlockPos()),
						"Fixture: the Stand's aim is not the wall in front of the cow: " + standAim);
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(standAim, helper.getLevel());
				charged[0] = scene.release("heavy_charged");
				helper.assertTrue(charged[0] instanceof StandEntityChargedHeavy && StandSkillWalls.isMarked(charged[0]),
						"Fixture: " + charged[0] + " is not the marked charged heavy punch");
				return false;
			}
			if (tick < 3 || charged[0].punchedTarget == null) {
				return false;
			}
			helper.assertTrue(charged[0].punchedTarget.getMainEntity() == cow, "The marked charged heavy punch of a manually controlled"
					+ " Stand without the aim assist struck " + charged[0].punchedTarget + " instead of the cow behind the wall in"
					+ " the Stand's aim, in its reach");
			return true;
		}, 80, () -> "The marked charged heavy punch of a manually controlled Stand did not strike");
	}

	// The same without manual control: a Stand that aims on its own (StandEntity.aimsWithoutUser, an add-on Stand that hunts
	// by itself), its aim a wall in its reach, a mob behind the wall in the reach of its user and out of the Stand's. Its ray
	// reaches as far as its user's (1.16 getAimDistance), so it meets the mob: the perform start takes no wall as the lunge
	// target (StandEntityHeavyPunchChargedAbility.capturePerformTarget, the lunge flag false), the Stand lunges, and the
	// end of the punch strikes the mob.
	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH + "charged_self_aiming")
	public static void selfAimingMarkedChargedHeavyPunchWithoutAimAssistLungesAtTheMobBehindTheWallOutOfReachAtItsStart(
			GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		user.noPhysics = true;
		user.moveTo(origin.x, origin.y, origin.z, 0, 0);
		user.setYHeadRot(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: could not add the Stand user");
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "Fixture: could not summon Star Platinum");
		@SuppressWarnings("unchecked")
		EntityType<? extends StandEntity> standType = (EntityType<? extends StandEntity>) power.getSummonedStandEntity().getType();
		type.forceUnsummon(user, power);
		StandEntity stand = new OwnAimStand(standType, helper.getLevel());
		stand.withStandType(type);
		stand.refreshDimensions();
		stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
		power.setSummonedStand(stand);
		helper.assertTrue(helper.getLevel().addFreshEntity(stand), "Fixture: could not add the Stand");
		stand.setNoGravity(true);
		stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
		StandEntity.updateStandStatAttributes(stand, user);
		power.setStamina(power.getMaxStamina());
		stand.updateStrengthMultipliers();
		var settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
		settings.standAttackTargetLock = false;
		settings.standAimAssist = false;
		helper.assertTrue(stand.getUser() == user && !stand.isManuallyControlled() && stand.aimsWithoutUser(),
				"Fixture: the Stand is not a self-aiming Stand of its user");
		// the Stand idles 0.75 blocks to the right of its user and 0.75 behind it; the wall is on its line of sight
		BlockPos wallTop = helper.absolutePos(new BlockPos(2, 3, 3));
		BlockPos wallBottom = helper.absolutePos(new BlockPos(2, 2, 3));
		BlockState wallTopBefore = helper.getLevel().getBlockState(wallTop);
		BlockState wallBottomBefore = helper.getLevel().getBlockState(wallBottom);
		helper.getLevel().setBlockAndUpdate(wallTop, Blocks.BEDROCK.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(wallBottom, Blocks.BEDROCK.defaultBlockState());
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Fixture: could not create a cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.noPhysics = true;
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		cow.setHealth(500);
		// behind the wall from the idle Stand, and on the line of its lunge, straight ahead of its user
		cow.moveTo(origin.x + 0.375, origin.y + 0.8, origin.z + 3.6, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: could not add a cow");
		markedStandType = power.getAbility("punch").getAbilityId().powerTypeId();
		StandSkillWalls.markAttacksThroughWalls(markedStandType);
		boolean[] closed = new boolean[1];
		Runnable close = () -> {
			if (closed[0]) return;
			closed[0] = true;
			unmark();
			if (power.isSummoned()) type.forceUnsummon(user, power);
			cow.discard();
			user.discard();
			helper.getLevel().setBlockAndUpdate(wallTop, wallTopBefore);
			helper.getLevel().setBlockAndUpdate(wallBottom, wallBottomBefore);
		};
		boolean[] finished = new boolean[1];
		int[] ticks = new int[1];
		EntityActionInstance[] charged = new EntityActionInstance[1];
		Boolean[] reachAtStart = new Boolean[1];
		helper.onEachTick(() -> {
			if (finished[0]) return;
			try {
				int tick = ++ticks[0];
				if (tick == 3) {
					// the aim the Stand puts into its own aim: the wall in front of the cow
					ActionTarget standAim = HitResultUtil.clip(stand.getEyePosition(), stand.getLookAngle(), 5, 5,
							helper.getLevel(), entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity), stand, 0,
							true);
					helper.assertTrue(standAim.getType() == ActionTarget.TargetType.BLOCK && wallTop.equals(standAim.getBlockPos()),
							"Fixture: the Stand's aim is not the wall in front of the cow: " + standAim);
					LivingComponentAction.getComponent(stand).entityAim.setTarget(standAim);
					EntityActionInstance action = ((EntityActionType) power.getAbility("heavy_charged"))
							.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
					LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
					charged[0] = action;
					helper.assertTrue(action instanceof StandEntityChargedHeavy && stand.getCurStandAction() == action
							&& StandSkillWalls.isMarked(action), "Fixture: " + action + " is not the marked charged heavy punch");
					action.onKeyRelease(user);
				}
				else if (tick > 3) {
					if (reachAtStart[0] == null && charged[0].getPhase() == ActionPhase.PERFORM) {
						reachAtStart[0] = !StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow))
								.isEmpty(helper.getLevel());
					}
					if (charged[0].punchedTarget != null) {
						helper.assertTrue(Boolean.FALSE.equals(reachAtStart[0]), "Fixture: the Stand could reach the cow when it"
								+ " started to punch; Stand at " + stand.position() + ", cow at " + cow.position());
						helper.assertTrue(charged[0].punchedTarget.getMainEntity() == cow, "The marked charged heavy punch of a"
								+ " Stand that aims on its own, without the aim assist, struck " + charged[0].punchedTarget
								+ " instead of lunging at the cow behind the wall in its aim, out of its reach only at its start;"
								+ " Stand at " + stand.position());
						finished[0] = true;
						close.run();
						helper.succeed();
					}
					else if (tick >= 80) {
						finished[0] = true;
						close.run();
						helper.fail("The marked charged heavy punch of a Stand that aims on its own did not strike");
					}
				}
			}
			catch (RuntimeException | Error error) {
				finished[0] = true;
				close.run();
				throw error;
			}
		});
	}

	// what an add-on Stand that hunts on its own does
	private static final class OwnAimStand extends StandEntity {
		OwnAimStand(EntityType<? extends StandEntity> type, Level level) {
			super(type, level);
		}

		@Override
		public boolean aimsWithoutUser() {
			return true;
		}
	}

	private static void unmark() {
		if (markedStandType != null) {
			StandSkillWalls.unmarkAttacksThroughWalls(markedStandType);
			markedStandType = null;
		}
	}

	@AfterBatch(batch = BATCH + "charged_reach")
	public static void releaseChargedReachBatch(ServerLevel level) {
		unmark();
	}

	@AfterBatch(batch = BATCH + "charged_self_aiming")
	public static void releaseChargedSelfAimingBatch(ServerLevel level) {
		unmark();
	}
}

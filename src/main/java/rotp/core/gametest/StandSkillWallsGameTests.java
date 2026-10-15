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
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
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
				boolean keepsPressTarget = StandEntityPunchAbility.shouldRetainPunchTarget(scene.stand, punch.ability);
				helper.assertTrue(keepsPressTarget == (keepAttackTarget || manualControl),
						"Fixture: the punch " + how + (keepsPressTarget ? " keeps" : " does not keep") + " its press target");
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
				// the charged heavy punch's own fallback aim, read before the barrage breaks the wall
				LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
				EntityActionInstance charged = scene.action("heavy_charged");
				component.setAction(charged, scene.user, SyncType.NO_SYNC);
				ActionTarget chargedAim = StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, charged, 1.0F);
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

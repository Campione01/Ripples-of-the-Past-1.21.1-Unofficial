package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.compat.v1_21_4.missingmethods._EntitySelector;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.syncheddata.SynchedDataExtended;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
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

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The aim assist per attack, as 1.16 kept or refused the target an attack was pressed at: the light punch keeps the
 * block or the mob it was pressed at (StandEntityLightAttack.standKeepsTarget), the heavy punch only a mob it can attack
 * (StandEntityHeavyAttack.standKeepsTarget), the barrage nothing (StandEntityMeleeBarrage.checkStandTarget); a pressed
 * mob the Stand cannot attack is refused (PowerBaseImpl.checkTarget, StandEntity.canAttack). What an attack does not
 * keep, StandEntity.punch aims again with the precision ray (aimWithThisOrUser, precisionRayTrace), whose boxes take a
 * mob within about 1.9 blocks of the eyes before the ground. Without the aim assist every attack strikes what the
 * crosshair meets. Each test has its own batch: the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAttackAimRulesGameTests {
	private static final String BATCH = "stand_attack_aim_rules_";
	private static final short KEY = 177;

	private StandAttackAimRulesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy")
	public static void heavyPunchAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage")
	public static void barrageAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch")
	public static void punchAtTheGroundStrikesTheBlockItWasPressedAtAsIn116(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, true);
	}

	// the charged heavy punch follows the heavy punch's rule (StandEntityHeavyAttack.standKeepsTarget kept only an entity),
	// released as a client's key release ends its charge
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_charged")
	public static void chargedHeavyPunchAtTheGroundHitsTheMobInThePrecisionBoxesAsIn116(GameTestHelper helper) {
		atTheGround(helper, "heavy_charged", InputMethod.HOLD, true);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_charged_off")
	public static void chargedHeavyPunchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "heavy_charged", InputMethod.HOLD, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_heavy_off")
	public static void heavyPunchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "heavy_punch", InputMethod.CLICK, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_barrage_off")
	public static void barrageWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "barrage", InputMethod.HOLD, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "ground_punch_off")
	public static void punchWithoutAimAssistStrikesTheGroundOnTheCrosshair(GameTestHelper helper) {
		atTheGround(helper, "punch", InputMethod.CLICK, false);
	}

	// the crosshair on the ground 2 blocks ahead, a cow beside the crosshair inside Star Platinum's precision boxes. For
	// the charged heavy punch the far side of the ground 1 block ahead: its perform starts with the Stand at its idle place
	// behind its user, from whose eyes the ground 2 blocks ahead is out of reach, so that no rule would decide anything there
	private static void atTheGround(GameTestHelper helper, String name, InputMethod method, boolean aimAssist) {
		Scene scene = new Scene(helper, aimAssist);
		boolean charged = name.equals("heavy_charged");
		BlockPos ground = charged ? scene.groundStrip().north() : scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1).add(0, 0, charged ? 0.4 : 0));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at " + ground
				+ ": " + scene.vanillaPick());
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow before the ground");
		boolean mobFirst = aimAssist && !name.equals("punch");
		String how = name + (aimAssist ? "" : " without the aim assist");
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = name.equals("heavy_charged") ? scene.release(name) : scene.press(name, method);
				helper.assertTrue(action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch
						|| action[0] instanceof StandEntityChargedHeavy
						|| StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]),
						"Fixture: the " + name + " input started " + action[0]);
				if (mobFirst && name.equals("heavy_punch")) {
					// 1.16 StandEntityHeavyAttack.standKeepsTarget kept only an entity: the pressed ground was refused
					ActionTarget saved = savedTarget(action[0]);
					helper.assertTrue((saved == null || saved.getType() != ActionTarget.TargetType.BLOCK)
							&& (action[0].standRotationTarget == null
									|| action[0].standRotationTarget.getType() != ActionTarget.TargetType.BLOCK),
							"The " + how + " pressed at the ground saved " + saved + " and turns the Stand to "
									+ action[0].standRotationTarget + "; 1.16 kept no block for a heavy punch");
				}
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (mobFirst && charged && action[0].getPhase() != null
					&& action[0].getPhase().ordinal() < ActionPhase.PERFORM.ordinal()) {
				var standAim = LivingComponentAction.getAim(scene.stand);
				ActionTarget synced = standAim != null ? standAim.getTarget() : ActionTarget.EMPTY;
				ActionTarget reached = StandEntityPunchAbility.validatePunchTarget(scene.stand, synced.copy());
				helper.assertTrue(reached.getType() == ActionTarget.TargetType.BLOCK && ground.equals(reached.getBlockPos()),
						"Fixture: before the perform the Stand's synced aim " + synced + " is not the ground within its reach");
			}
			if (mobFirst && charged && action[0].getPhase() == ActionPhase.PERFORM) {
				// the lunge goes at the target its perform start took: the ground in the Stand's aim is dropped
				ActionTarget lunge = savedTarget(action[0]);
				helper.assertTrue(lunge == null || lunge.getType() != ActionTarget.TargetType.BLOCK, "The " + how
						+ " lunges at " + lunge + " instead of the cow in the precision boxes; 1.16 kept no block for a heavy punch");
			}
			if (tick == 5 && mobFirst && !name.equals("heavy_charged")) {
				// 1.16 placed the Stand from the task target, empty here (the barrage kept none, the heavy punch no block):
				// front3dOffset took the user's look ray, which meets nothing within its reach, so the farthest offset,
				// not a step in toward the cow the precision ray takes
				Vec3 offset = action[0].getSyncedStandOffset();
				double farthest = name.equals("barrage") ? Math.min(1.5, scene.stand.getEffectiveRange())
						: Math.min(2, scene.stand.getMaxRange());
				helper.assertTrue(farthest > 0.6 && offset != null && Math.abs(offset.z - farthest) < 0.01, "The " + how
						+ " placed the Stand at " + offset + " toward the cow it aims at; 1.16 stood along its user's look,"
						+ " at the farthest " + farthest);
			}
			ActionTarget struck = action[0].punchedTarget;
			boolean blockStruck = struck != null && struck.getType() == ActionTarget.TargetType.BLOCK;
			if (mobFirst) {
				helper.assertTrue(!blockStruck && scene.isStone(ground), "The " + how + " struck " + struck
						+ " instead of the cow in the precision boxes, which 1.16's precision ray took before the ground");
				return cow.getHealth() < health && tick > 15;
			}
			if (struck == null || struck.getType() == ActionTarget.TargetType.EMPTY) {
				helper.assertTrue(cow.getHealth() >= health, "The " + how + " hurt the cow beside the crosshair before it struck");
				return false;
			}
			helper.assertTrue(blockStruck && ground.equals(struck.getBlockPos()), "The " + how + " struck " + struck
					+ " instead of the ground on the crosshair" + (aimAssist ? ", the block 1.16's light punch kept from its press" : ""));
			return true;
		}, 60, () -> "The " + how + (mobFirst ? " never hit the cow in the precision boxes" : " never struck the ground"));
	}

	// the user's own wolf on the ground on the crosshair with the ground right behind it: 1.16 refused the pressed wolf
	// and aimed again with the precision ray, which skips it, so the cow beside is hit, never the ground
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_punch")
	public static void punchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_heavy")
	public static void heavyPunchAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "heavy_punch", InputMethod.CLICK);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "wolf_barrage")
	public static void barrageAtTheUsersOwnWolfOnTheGroundHitsTheMobBesideAsIn116(GameTestHelper helper) {
		atOwnWolfOnTheGround(helper, "barrage", InputMethod.HOLD);
	}

	private static void atOwnWolfOnTheGround(GameTestHelper helper, String name, InputMethod method) {
		Scene scene = new Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.besideTheCrosshair();
		Wolf wolf = scene.ownWolf(Vec3.upFromBottomCenterOf(ground, 1));
		// low on its front, so that the crosshair ray goes on into the ground under it
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.position().add(0, 0.15, -0.25));
		helper.assertTrue(scene.vanillaPick().getMainEntity() == wolf, "Fixture: the vanilla pick is " + scene.vanillaPick());
		ActionTarget behind = StandEntityPunchAbility.crosshairPick(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity));
		helper.assertTrue(behind.getType() == ActionTarget.TargetType.BLOCK && ground.equals(behind.getBlockPos()),
				"Fixture: past the wolf the crosshair does not meet the ground under it: " + behind);
		helper.assertTrue(StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() == cow,
				"Fixture: the precision ray does not take the cow beside");
		float wolfHealth = wolf.getHealth();
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				ActionTarget aim = scene.sendClientAims();
				helper.assertTrue(aim.getMainEntity() == wolf, "Fixture: a client's Stand aim, the marker, is " + aim);
				action[0] = scene.press(name, method);
				helper.assertTrue(action[0] != null, "Fixture: the " + name + " input started nothing");
				helper.assertTrue(action[0].standRotationTarget == null || action[0].standRotationTarget.getMainEntity() != wolf,
						"The " + name + " turned the Stand to the user's own wolf it was pressed at, which 1.16 refused");
				if (!name.equals("barrage")) {
					// with no pressed target the Stand stands off at its farthest, nothing being within 2 blocks on the look;
					// stepping up to the wolf 2 blocks ahead would leave it within a block of its user
					Vec3 offset = action[0].getSyncedStandOffset();
					helper.assertTrue(offset != null && offset.z > 1.0, "The " + name + " stepped the Stand up to the"
							+ " user's own wolf it was pressed at, which 1.16 refused: offset " + offset);
				}
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			helper.assertTrue(wolf.getHealth() >= wolfHealth, "The " + name + " hurt the user's own wolf");
			helper.assertTrue((struck == null || struck.getType() != ActionTarget.TargetType.BLOCK) && scene.isStone(ground),
					"The " + name + " struck " + struck + ", the ground behind the user's own wolf; 1.16's precision ray"
							+ " took the cow beside once the wolf was refused");
			return cow.getHealth() < health && tick > 15;
		}, 60, () -> "The " + name + " at the user's own wolf on the ground never hit the cow beside it");
	}

	// keep attack target on: the locked barrage keeps the target it was pressed at, but 1.16 refused a pressed entity the
	// Stand cannot attack (PowerBaseImpl.checkTarget, StandEntity.canAttack), so the user's own wolf is neither saved nor
	// turned to
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "wolf_locked_barrage")
	public static void lockedBarragePressedAtTheUsersOwnWolfKeepsNothingAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
		Wolf wolf = scene.ownWolf(scene.origin.add(0, 0.6, 2.0));
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, wolf.getBoundingBox().getCenter());
		helper.assertTrue(scene.vanillaPick().getMainEntity() == wolf, "Fixture: the vanilla pick is " + scene.vanillaPick());
		float health = wolf.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(action[0] instanceof StandEntityBarrageAbility.StandEntityBarrage
						&& !StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action[0]),
						"Fixture: the barrage input started " + action[0] + ", not the locked barrage");
				ActionTarget saved = savedTarget(action[0]);
				helper.assertTrue((saved == null || saved.getMainEntity() != wolf) && (action[0].standRotationTarget == null
						|| action[0].standRotationTarget.getMainEntity() != wolf), "The locked barrage pressed at the user's own"
								+ " wolf saved " + saved + " and turns the Stand to " + action[0].standRotationTarget
								+ "; 1.16 refused a pressed entity the Stand cannot attack");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(wolf.getHealth() >= health, "The locked barrage hurt the user's own wolf");
			return tick > 12;
		}, 30, () -> "The locked barrage test timed out");
	}

	// 1.16 kept the mob a light or heavy punch was pressed at as the task target while it stayed in range
	// (StandEntityTask.keepTarget, standKeepsTarget of StandEntityLightAttack and StandEntityHeavyAttack), turned the Stand
	// to it (StandEntityTask.rotateStand) and struck it while the Stand could reach it (StandEntity.aimWithThisOrUser), not
	// what the crosshair met by then. "Keep attack target" is off. Small precision boxes, so that only the kept target
	// finds the mob once it is off the crosshair.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_punch")
	public static void punchStrikesTheMobItWasPressedAtThatStepsAsideAsIn116(GameTestHelper helper) {
		pressedMobStepsAside(helper, "punch");
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_mob_heavy")
	public static void heavyPunchStrikesTheMobItWasPressedAtThatStepsAsideAsIn116(GameTestHelper helper) {
		pressedMobStepsAside(helper, "heavy_punch");
	}

	private static void pressedMobStepsAside(GameTestHelper helper, String name) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		Cow cow = scene.cow(0, 2.0);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the vanilla pick is " + scene.vanillaPick());
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press(name, InputMethod.CLICK);
				helper.assertTrue((action[0] instanceof StandEntityPunch || action[0] instanceof StandEntityHeavyPunch)
						&& action[0].punchedTarget == null, "Fixture: the " + name + " input started " + action[0]
								+ ", or it struck at once");
				// a step aside within the Stand's reach, off the crosshair; the client sends its aims again
				cow.moveTo(cow.getX() + 0.9, cow.getY(), cow.getZ(), 0, 0);
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getMainEntity() != cow
						&& StandEntityPunchAbility.userPrecisionRayTarget(scene.stand, scene.user).getMainEntity() != cow,
						"Fixture: the user's aims still meet the cow that stepped aside");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(turnedTo != null && turnedTo.getMainEntity() == cow, "The " + name + " turns the Stand to "
						+ turnedTo + " instead of the cow it was pressed at; 1.16 turned the Stand to its task target");
				return false;
			}
			helper.assertTrue(struck.getMainEntity() == cow && cow.getHealth() < health, "The " + name + " struck " + struck
					+ " instead of the cow it was pressed at, which stepped aside within the Stand's reach (Stand at "
					+ scene.stand.position() + ", cow at " + cow.position() + "); 1.16 struck the task target the press kept");
			return true;
		}, 60, () -> "The " + name + " never struck");
	}

	// 1.16 kept the block a light punch was pressed at too (StandEntityLightAttack.standKeepsTarget): its user turns from
	// the ground to a cow before the swing, and the punch strikes the ground
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "kept_block_punch")
	public static void punchStrikesTheBlockItWasPressedAtAfterItsUserTurnsAsIn116(GameTestHelper helper) {
		Scene scene = new Scene(helper, true);
		BlockPos ground = scene.groundStrip();
		Cow cow = scene.cow(-1.0, 1.6);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.upFromBottomCenterOf(ground, 1));
		helper.assertTrue(scene.vanillaPick().getType() == ActionTarget.TargetType.BLOCK
				&& ground.equals(scene.vanillaPick().getBlockPos()), "Fixture: the crosshair does not meet the ground at "
						+ ground + ": " + scene.vanillaPick());
		float health = cow.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.sendClientAims();
				action[0] = scene.press("punch", InputMethod.CLICK);
				helper.assertTrue(action[0] instanceof StandEntityPunch && action[0].punchedTarget == null,
						"Fixture: the punch input started " + action[0] + ", or it struck at once");
				scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
				scene.sendClientAims();
				helper.assertTrue(scene.vanillaPick().getMainEntity() == cow, "Fixture: the turned user's vanilla pick is "
						+ scene.vanillaPick());
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(cow.getHealth() >= health, "The punch pressed at the ground hurt the cow its user turned to;"
					+ " 1.16 struck the block the press kept");
			ActionTarget struck = action[0].punchedTarget;
			if (struck == null) {
				ActionTarget turnedTo = action[0].standRotationTarget;
				helper.assertTrue(turnedTo != null && turnedTo.getType() == ActionTarget.TargetType.BLOCK
						&& ground.equals(turnedTo.getBlockPos()), "The punch turns the Stand to " + turnedTo
								+ " instead of the ground it was pressed at; 1.16 turned the Stand to its task target");
				return false;
			}
			helper.assertTrue(struck.getType() == ActionTarget.TargetType.BLOCK && ground.equals(struck.getBlockPos()),
					"The punch pressed at the ground struck " + struck + " after its user turned; 1.16 struck the block"
							+ " the press kept");
			return tick > 15;
		}, 60, () -> "The punch pressed at the ground never struck it");
	}

	// the grab (a port move) takes only an entity the Stand can attack (StandEntityGrab picks with canStandHit); one that
	// becomes the user's own while held is spared by the grab's punches, as 1.16's StandEntity.attackEntity refused any
	// main target the Stand could not harm
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "grab_punch")
	public static void grabPunchSparesAHeldWolfThatBecameTheUsersOwn(GameTestHelper helper) {
		heldWolfBecameOwn(helper, "punch");
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "grab_heavy")
	public static void grabHeavyPunchSparesAHeldWolfThatBecameTheUsersOwn(GameTestHelper helper) {
		heldWolfBecameOwn(helper, "heavy_punch");
	}

	private static void heldWolfBecameOwn(GameTestHelper helper, String name) {
		Scene scene = new Scene(helper, true);
		LivingEntity[] owner = new LivingEntity[1];
		Wolf wolf = scene.wolf(scene.origin.add(0, 1.0, 1.5), () -> owner[0]);
		helper.assertTrue(StandEntityPunchAbility.canStandHit(scene.stand, wolf), "Fixture: the Stand cannot attack the wild wolf");
		float health = wolf.getHealth();
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			if (tick == 3) {
				scene.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(wolf);
				owner[0] = scene.user;
				wolf.setTame(true, false);
				wolf.setOwnerUUID(scene.user.getUUID());
				helper.assertTrue(!StandEntityPunchAbility.canStandHit(scene.stand, wolf),
						"Fixture: the Stand can still attack the wolf tamed by its user");
				action[0] = scene.press(name, InputMethod.CLICK);
				helper.assertTrue(action[0] != null && action[0].ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB,
						"Fixture: the " + name + " input started " + action[0] + ", not its grab variation");
				return false;
			}
			if (tick < 3) {
				return false;
			}
			helper.assertTrue(wolf.getHealth() >= health, "The grab " + name + " hurt the held wolf that became the user's"
					+ " own; 1.16's StandEntity.attackEntity spared a main target the Stand could not harm");
			ActionTarget struck = action[0].punchedTarget;
			return struck != null && struck.getMainEntity() == wolf && tick > 20;
		}, 60, () -> "The grab " + name + " never swung at the held wolf");
	}

	// 1.16 StandEntityMeleeBarrage.checkStandTarget refused every pressed target, so under manual control each hit aimed
	// again from the Stand's own eyes (StandEntity.punch, aimWithThisOrUser skipping the user, precisionRayTrace(this,
	// getAimDistance)): pressed at one cow, the barrage hits the other once the Stand turns to it. Without the aim assist
	// the Stand's own crosshair ray decides the same way. With "keep attack target" (a port setting) it stays on the first.
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage")
	public static void manuallyControlledBarrageAimsEveryHitFromTheStandsEyesAsIn116(GameTestHelper helper) {
		manualBarrage(helper, true, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage_off")
	public static void manuallyControlledBarrageWithoutAimAssistAimsEveryHitWithTheStandsCrosshair(GameTestHelper helper) {
		manualBarrage(helper, false, false);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH + "manual_barrage_lock")
	public static void manuallyControlledBarrageWithKeepAttackTargetStaysOnItsPressedTarget(GameTestHelper helper) {
		manualBarrage(helper, true, true);
	}

	private static void manualBarrage(GameTestHelper helper, boolean aimAssist, boolean keepTarget) {
		Scene scene = new Scene(helper, aimAssist);
		scene.user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = keepTarget;
		// small precision boxes, so that only the cow the Stand faces is on its rays, with the assist on too
		scene.precision(0);
		Vec3 standPos = scene.origin.add(0, 0, 1);
		Cow first = scene.cow(-1.2, 2.2);
		Cow second = scene.cow(1.2, 2.2);
		scene.stand.setManuallyControlled(true);
		helper.assertTrue(scene.stand.isManuallyControlled() && scene.stand.aimsWithoutUser(),
				"Fixture: the Stand is not manually controlled");
		String how = (keepTarget ? "locked " : "") + "barrage of a manually controlled Stand"
				+ (aimAssist ? "" : " without the aim assist");
		float firstHealth = first.getHealth();
		float secondHealth = second.getHealth();
		float[] firstAfterTurn = new float[1];
		EntityActionInstance[] action = new EntityActionInstance[1];
		scene.run(tick -> {
			Cow facing = tick < 14 ? first : second;
			scene.standAt(standPos, facing);
			if (tick == 3) {
				for (Cow cow : List.of(first, second)) {
					scene.standAt(standPos, cow);
					ActionTarget aim = StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1,
							entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity),
							StandEntityPunchAbility.CrosshairRule.NONE, true);
					helper.assertTrue(aim.getMainEntity() == cow, "Fixture: the Stand's own aim, facing " + cow
							+ ", took " + aim);
				}
				scene.standAt(standPos, first);
				// the Stand aim a client syncs under manual control
				LivingComponentAction.getComponent(scene.stand).entityAim.setTargetFromClient(new ActionTarget(first),
						helper.getLevel());
				action[0] = scene.press("barrage", InputMethod.HOLD);
				helper.assertTrue(action[0] instanceof StandEntityBarrageAbility.StandEntityBarrage,
						"Fixture: the barrage input started " + action[0]);
				return false;
			}
			if (tick < 3) {
				return false;
			}
			if (tick == 13) {
				helper.assertTrue(first.getHealth() < firstHealth && second.getHealth() >= secondHealth,
						"The " + how + " pressed at the cow it faces did not hit it alone: " + first.getHealth() + " and "
								+ second.getHealth() + " of " + firstHealth);
			}
			if (tick == 14) {
				firstAfterTurn[0] = first.getHealth();
			}
			if (tick < 30) {
				return false;
			}
			if (keepTarget) {
				helper.assertTrue(second.getHealth() >= secondHealth && first.getHealth() < firstAfterTurn[0],
						"The " + how + " left the cow it was pressed at when the Stand turned to the other: the first at "
								+ first.getHealth() + " (" + firstAfterTurn[0] + " at the turn), the other at " + second.getHealth());
			}
			else {
				helper.assertTrue(first.getHealth() >= firstAfterTurn[0] && second.getHealth() < secondHealth,
						"The " + how + " kept hitting the cow it was pressed at after the Stand turned to the other: the first"
								+ " at " + first.getHealth() + " (" + firstAfterTurn[0] + " at the turn), the other at "
								+ second.getHealth() + "; 1.16 aimed every hit again from the Stand's eyes");
			}
			return true;
		}, 40, () -> "The " + how + " test timed out");
	}

	// 1.16 refused every pressed barrage target whoever the user (StandEntityMeleeBarrage.checkStandTarget), so a mob user's
	// barrage aimed each hit again from the mob's eyes (StandEntity.punch, aimWithThisOrUser with the mob as the user):
	// pressed at one cow, it hits the other once the mob looks at it. A mob has no "keep attack target" setting.
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "mob_barrage")
	public static void mobUsersBarrageAimsEveryHitFromTheMobsEyesAsIn116(GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(type != null && zombie != null, "Fixture: could not create the zombie or find Star Platinum");
		List<Entity> entities = new ArrayList<>(List.of(zombie));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
		zombie.moveTo(origin.x, origin.y, origin.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Fixture: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "Fixture: could not give the zombie Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Fixture: the zombie's Stand is missing");
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			// small precision boxes, so that only the cow the zombie looks at is on its ray
			stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(0);
			Cow first = mobBarrageCow(helper, entities, origin.add(-1.0, 0, 1.8));
			Cow second = mobBarrageCow(helper, entities, origin.add(1.0, 0, 1.8));
			EntityActionType barrage = (EntityActionType) power.getAbility("barrage");
			helper.assertTrue(barrage != null, "Fixture: Star Platinum has no barrage");
			zombie.lookAt(EntityAnchorArgument.Anchor.EYES, first.getBoundingBox().getCenter());
			LivingComponentAction.getComponent(zombie).entityAim.setTarget(new ActionTarget(first));
			EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), zombie, stand, null);
			LivingComponentAction standActions = LivingComponentAction.getComponent(stand);
			standActions.setAction(action, zombie, SyncType.NO_SYNC);
			float firstHealth = first.getHealth();
			for (int tick = 0; tick < 12 && first.getHealth() >= firstHealth; tick++) {
				stand.tickCount++;
				standActions.tick();
			}
			helper.assertTrue(first.getHealth() < firstHealth && action.punchedTarget != null
					&& action.punchedTarget.getMainEntity() == first, "Fixture: the zombie's barrage pressed at the cow it looks at"
							+ " did not hit it: " + action.punchedTarget);
			zombie.lookAt(EntityAnchorArgument.Anchor.EYES, second.getBoundingBox().getCenter());
			float secondHealth = second.getHealth();
			for (int tick = 0; tick < 12 && second.getHealth() >= secondHealth; tick++) {
				stand.tickCount++;
				standActions.tick();
			}
			helper.assertTrue(second.getHealth() < secondHealth && action.punchedTarget != null
					&& action.punchedTarget.getMainEntity() == second, "The zombie's barrage stayed on " + action.punchedTarget
							+ ", the cow it was pressed at, after the zombie looked at the other; 1.16 aimed every hit again from"
							+ " the mob's eyes");
			helper.assertTrue(!StandEntityPunchAbility.shouldRetainPunchTarget(stand, barrage),
					"A mob user's barrage keeps the target it was pressed at; 1.16 aimed every hit again");
			standActions.setAction(null, zombie, SyncType.NO_SYNC);
		}
		finally {
			if (power.isSummoned()) {
				type.forceUnsummon(zombie, power);
			}
			entities.forEach(Entity::discard);
		}
		helper.succeed();
	}

	// 1.16 StandEntityMeleeBarrage.checkStandTarget let only a barrage-clash opponent become the task target, when a hit's
	// aim met it (StandEntity.punch 1788-1796); aimWithThisOrUser then kept it while in reach (StandEntity 1676-1678),
	// wherever the user looked, and getOffsetFromUser placed the Stand from it; out of reach, the barrage aimed again
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "clash_lock")
	public static void defaultBarrageStaysOnItsBarrageClashOpponentAsIn116(GameTestHelper helper) {
		barrageClash(helper, true);
	}

	// the user aims at a cow from the start of the clash: no hit meets the opposing Stand, so 1.16's task target stayed
	// empty (checkStandTarget refused the cow) and every hit aimed again and struck the cow
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH + "clash_aim")
	public static void defaultBarrageInABarrageClashHitsWhatItsUserAimsAtUntilAHitMeetsTheOpposingStandAsIn116(
			GameTestHelper helper) {
		barrageClash(helper, false);
	}

	private static void barrageClash(GameTestHelper helper, boolean opponentFirst) {
		Scene scene = new Scene(helper, true);
		scene.precision(0);
		Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		rival.setNoGravity(true);
		rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		rival.setHealth(500);
		rival.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 4, 180, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Fixture: could not add the rival Stand user");
		scene.entities.add(rival);
		StandPower rivalPower = PowerClass.STAND.attachGet(rival);
		try {
			helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(scene.type)).status()
					== StandPowerTransitions.Status.APPLIED && scene.type.summon(rival, rivalPower),
					"Fixture: could not give the rival Star Platinum");
			StandEntity opponent = rivalPower.getSummonedStandEntity();
			helper.assertTrue(opponent != null, "Fixture: the rival's Stand is missing");
			opponent.setNoGravity(true);
			opponent.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2, 180, 0);
			Cow cow = scene.cow(1.6, 0.3);
			Entity aimed = opponentFirst ? opponent : cow;
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, aimed.getBoundingBox().getCenter());
			EntityActionType barrage = (EntityActionType) scene.power.getAbility("barrage");
			EntityActionInstance action = barrage.initActionOnAbilityUse(helper.getLevel(), scene.user, scene.stand, null);
			LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
			standActions.setAction(action, scene.user, SyncType.NO_SYNC);
			helper.assertTrue(StandEntityBarrageAbility.isDirectionalBarrage(scene.stand, action)
					&& StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, action, 1).getMainEntity() == aimed,
					"Fixture: the default barrage does not aim at " + aimed + ", where the user looks");
			Method clash = StandEntity.class.getDeclaredMethod("setBarrageClashOpponent", Entity.class);
			clash.setAccessible(true);
			clash.invoke(scene.stand, opponent);
			helper.assertTrue(scene.stand.barrageClashOpponent().orElse(null) == opponent
					&& opponent.barrageClashOpponent().orElse(null) == scene.stand, "Fixture: the two Stands are not in a barrage clash");
			float health = cow.getHealth();
			if (!opponentFirst) {
				for (int tick = 0; tick < 6; tick++) {
					scene.stand.tickCount++;
					standActions.tick();
				}
				helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow
						&& cow.getHealth() < health, "The default barrage in a barrage clash struck " + action.punchedTarget
								+ " instead of the cow its user aims at; 1.16 took the opposing Stand as its target only once"
								+ " a hit met it");
				ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
				helper.assertTrue(clientHeld.isEmpty(helper.getLevel()), "Clients hold " + clientHeld + " for the default"
						+ " barrage in a barrage clash no hit of which met the opposing Stand, so the Stand turns away from"
						+ " the cow its user aims at; 1.16 sent clients the task target, still empty");
			}
			else {
				opponentFirst(helper, scene, action, opponent, cow);
			}
			standActions.setAction(null, scene.user, SyncType.NO_SYNC);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: could not start the barrage clash", e);
		}
		finally {
			scene.stand.barrageClashStopped();
			if (rivalPower.isSummoned()) {
				scene.type.forceUnsummon(rival, rivalPower);
			}
			scene.close();
		}
		helper.succeed();
	}

	// the user looks at the opposing Stand until a hit meets it, then turns to the cow
	private static void opponentFirst(GameTestHelper helper, Scene scene, EntityActionInstance action, StandEntity opponent,
			Cow cow) {
		LivingComponentAction standActions = LivingComponentAction.getComponent(scene.stand);
		float health = cow.getHealth();
		double nearest = Math.min(0.5, scene.stand.getEffectiveRange());
		double farthest = Math.min(1.5, scene.stand.getEffectiveRange());
		double fromOpponent = Mth.clamp(opponent.position().subtract(scene.user.position()).horizontalDistance()
				- (1.0 + opponent.getBbWidth() / 2.0), nearest, farthest);
		helper.assertTrue(Math.abs(fromOpponent - farthest) > 0.1, "Fixture: the Stand placed from the opposing Stand"
				+ " stands at " + fromOpponent + ", as along the user's look");
		for (int tick = 0; tick < 6 && action.punchedTarget == null; tick++) {
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == opponent,
				"Fixture: the barrage at the opposing Stand its user looks at struck " + action.punchedTarget);
		assertClientsHold(helper, scene, action, opponent, "once a hit met it");
		// that tick placed the Stand before its first hit, while 1.16's task target was still empty
		Vec3 offset = action.getSyncedStandOffset();
		helper.assertTrue(offset != null && Math.abs(offset.z - farthest) < 0.01, "The default barrage in a barrage clash"
				+ " placed the Stand at " + offset + " from the opposing Stand before a hit met it; 1.16 stood along its"
				+ " user's look, at the farthest " + farthest);
		scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
		helper.assertTrue(StandEntityPunchAbility.aimWithThisOrUser(scene.stand, scene.user, 1,
				entity -> entity != scene.stand && StandEntityPunchAbility.canStandHit(scene.stand, entity),
				StandEntityPunchAbility.CrosshairRule.NONE, true).getMainEntity() == cow,
				"Fixture: the user's aim does not take the cow once turned to it");
		for (int tick = 0; tick < 6; tick++) {
			scene.stand.tickCount++;
			standActions.tick();
		}
		offset = action.getSyncedStandOffset();
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == opponent
				&& cow.getHealth() >= health, "The default barrage in a barrage clash struck " + action.punchedTarget
						+ " where its user looks instead of the opposing Stand a hit had met; 1.16 kept it as its target");
		helper.assertTrue(offset != null && Math.abs(offset.z - fromOpponent) < 0.01, "The default barrage on its"
				+ " clash opponent placed the Stand at " + offset + "; 1.16 placed it from its target, at " + fromOpponent);
		assertClientsHold(helper, scene, action, opponent, "while its user looks at the cow");
		opponent.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 8, 180, 0);
		for (int tick = 0; tick < 6; tick++) {
			scene.stand.tickCount++;
			standActions.tick();
		}
		helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getMainEntity() == cow,
				"The default barrage kept its clash opponent out of reach: it struck " + action.punchedTarget);
		ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
		helper.assertTrue(clientHeld.isEmpty(helper.getLevel()), "Clients still hold " + clientHeld + " for the default"
				+ " barrage whose clash opponent is out of reach and whose hits strike the cow");
	}

	// the server turns the Stand to the opposing Stand it holds; every client must turn it there too
	private static void assertClientsHold(GameTestHelper helper, Scene scene, EntityActionInstance action,
			StandEntity opponent, String when) {
		ActionTarget serverHeld = StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand, action, 1);
		helper.assertTrue(serverHeld.getMainEntity() == opponent, "Fixture: the server does not hold the opposing Stand "
				+ when + ": " + serverHeld);
		ActionTarget clientHeld = clientHeldClashOpponent(helper, scene.stand, action);
		helper.assertTrue(clientHeld.getMainEntity() == opponent, "Clients hold " + clientHeld + " for the default barrage"
				+ " in a barrage clash " + when + ", where the server holds the opposing Stand, so the Stand turns to its"
				+ " user's aim on every client; 1.16 sent clients the task target (StandEntity.setTaskTarget,"
				+ " TrStandTaskTargetPacket)");
	}

	// what a client decides from: a barrage with only the synced data the server sends with the action
	// (SyncActionInstanceData.onStartedTracking), without the server's punchedTarget
	private static ActionTarget clientHeldClashOpponent(GameTestHelper helper, StandEntity stand, EntityActionInstance action) {
		SynchedDataExtended sent = action.synchedData.getDataSyncher();
		helper.assertTrue(sent != null, "The default barrage sends clients no synced data");
		StandEntityBarrage client = new StandEntityBarrage(action.ability);
		try {
			java.lang.reflect.Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(client, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client copy its Stand", e);
		}
		List<SynchedEntityData.DataValue<?>> values = sent.syncOnStartedTracking();
		if (values != null) {
			client.synchedData.getDataSyncher().assignValues(values);
		}
		return client.heldClashOpponentAsSynced(stand);
	}

	// the target the action saved from its press (EntityActionInstance.actionTargetSnapshot)
	static ActionTarget savedTarget(EntityActionInstance action) {
		try {
			java.lang.reflect.Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
			field.setAccessible(true);
			return (ActionTarget) field.get(action);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot read the action's saved target", e);
		}
	}

	private static Cow mobBarrageCow(GameTestHelper helper, List<Entity> entities, Vec3 pos) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Fixture: could not create a cow");
		cow.setNoAi(true);
		cow.setNoGravity(true);
		cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		cow.setHealth(500);
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: could not add a cow");
		entities.add(cow);
		return cow;
	}

	private interface Step {
		boolean run(int tick);
	}

	private record PlacedBlock(BlockPos pos, BlockState previous) {}

	static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final Vec3 origin;
		final List<Entity> entities = new ArrayList<>();
		final List<PlacedBlock> placedBlocks = new ArrayList<>();
		int ticks;
		boolean finished;
		boolean closed;

		Scene(GameTestHelper helper, boolean aimAssist) {
			this.helper = helper;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
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
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			var settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
			settings.standAttackTargetLock = false;
			settings.standAimAssist = aimAssist;
		}

		// stone at the user's feet level from 1 to 3 blocks ahead; returns the one 2 blocks ahead
		BlockPos groundStrip() {
			BlockPos feet = BlockPos.containing(origin.x, origin.y - 0.5, origin.z);
			for (int z = 1; z <= 3; z++) {
				block(feet.south(z), Blocks.STONE.defaultBlockState());
			}
			return feet.south(2);
		}

		void precision(double precision) {
			user.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
			StandEntity.updateStandStatAttributes(stand, user);
			stand.getAttribute(ModEntityAttributes.STAND_PRECISION).setBaseValue(precision);
			helper.assertTrue(stand.getPrecision() == precision, "Fixture: the Stand's precision is " + stand.getPrecision());
		}

		// the Stand at the given place, its eyes turned to the middle of the entity, as its user turns it under manual control
		void standAt(Vec3 pos, Entity facing) {
			stand.moveTo(pos.x, pos.y, pos.z, stand.getYRot(), stand.getXRot());
			stand.setDeltaMovement(Vec3.ZERO);
			stand.lookAt(EntityAnchorArgument.Anchor.EYES, facing.getBoundingBox().getCenter());
		}

		boolean isStone(BlockPos pos) {
			return helper.getLevel().getBlockState(pos).is(Blocks.STONE);
		}

		void block(BlockPos pos, BlockState state) {
			placedBlocks.add(new PlacedBlock(pos, helper.getLevel().getBlockState(pos)));
			helper.getLevel().setBlockAndUpdate(pos, state);
		}

		Cow cow(double x, double z) {
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			cow.setHealth(500);
			cow.moveTo(origin.x + x, origin.y, origin.z + z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		// within Star Platinum's precision boxes of the user's eyes, off the crosshair
		Cow besideTheCrosshair() {
			Cow cow = cow(1.2, 0.8);
			helper.assertTrue(cow.getBoundingBox().distanceToSqr(user.getEyePosition()) < 1.9 * 1.9,
					"Fixture: the cow is not within 1.9 blocks of the user's eyes");
			return cow;
		}

		// the user's own tame wolf; the Stand cannot attack it
		Wolf ownWolf(Vec3 pos) {
			Wolf wolf = wolf(pos, () -> user);
			wolf.setTame(true, false);
			wolf.setOwnerUUID(user.getUUID());
			helper.assertTrue(wolf.getOwner() == user && !StandEntityPunchAbility.canStandHit(stand, wolf),
					"Fixture: the wolf is not the user's own, or the Stand can attack it");
			return wolf;
		}

		Wolf wolf(Vec3 pos, Supplier<LivingEntity> owner) {
			Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel()) {
				// the level finds a real owner, a server player, by its UUID; this mock user is not one
				@Override
				public LivingEntity getOwner() {
					return owner.get();
				}
			};
			wolf.setNoAi(true);
			wolf.setNoGravity(true);
			wolf.noPhysics = true;
			wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
			wolf.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
			wolf.setHealth(500);
			wolf.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(wolf), "Could not add a wolf");
			entities.add(wolf);
			return wolf;
		}

		// what the vanilla pick meets; the Stand standing on its user here is no part of a real view
		Predicate<Entity> pickable() {
			return entity -> entity != stand && _EntitySelector.CAN_BE_PICKED.test(entity);
		}

		ActionTarget vanillaPick() {
			return StandEntityPunchAbility.crosshairPick(stand, user, 1, pickable());
		}

		// the aims a client sends, its vanilla pick and its Stand aim, through the packet handlers' own step
		ActionTarget sendClientAims() {
			ActionTarget pick = vanillaPick();
			ActionTarget standAim = StandEntityPunchAbility.userStandAim(stand, user, pick,
					entity -> StandEntityAbility.canDefaultTargetEntityForAiming(stand, entity));
			LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(pick, helper.getLevel());
			LivingComponentAction.getComponent(stand).entityAim.setTargetFromClient(standAim, helper.getLevel());
			return standAim;
		}

		EntityActionInstance press(String name, InputMethod method) {
			Ability base = power.getAbility(name);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, method),
					"The " + name + " input was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, method, 0, buffer, base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The " + name + " key press failed");
			return held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
		}

		// a held move started on the Stand and its key released, as StandSkillWallsGameTests drives the charged heavy punch
		EntityActionInstance release(String name) {
			EntityActionInstance action = ((EntityActionType) power.getAbility(name))
					.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the " + name + " did not start on the Stand");
			action.onKeyRelease(user);
			return action;
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
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			for (int i = placedBlocks.size() - 1; i >= 0; i--) {
				helper.getLevel().setBlockAndUpdate(placedBlocks.get(i).pos(), placedBlocks.get(i).previous());
			}
			placedBlocks.clear();
		}
	}
}

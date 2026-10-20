package rotp.core.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility.HealingAction;
import rotp.core.impl.stands.hierophant.HGBarrierEntity;
import rotp.core.impl.stands.hierophant.HGStringEntity;
import rotp.core.impl.stands.hierophant.HierophantGreenEntity;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility.StringAttackShot;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.AimingEntity;
import rotp.core.util.functions.MathUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntityTask.rotateStand: a Stand turned only to its task's target (StandEntityAction.rotateStandTowardsTarget)
 * and, while its task held none, a following one along its user's look (StandEntityAction.rotateStand,
 * StandEntity.defaultRotation).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandTaskTargetTurnGameTests {
	private static final double EPS = 1.0E-4D;
	// the user looks south (+z), a cow 3 blocks east (+x) of it
	private static final float VIEW_YAW = 0.0F;
	private static final Vec3 COW_FROM_USER = new Vec3(3.0D, 0.0D, 0.0D);

	private StandTaskTargetTurnGameTests() {}

	/*
	 * 1.16 StandEntityTask.tick dropped the target keepTarget no longer passed (setTaskTarget(EMPTY)): an entity past the
	 * action's 8 blocks (Action.checkRangeAndTarget) or one that has lain dead for 20 ticks. From then on rotateStand turned
	 * the following Stand along its user's look, whatever the action had turned it to.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aFollowingStandTurnsBackToItsUsersLookOnceItsTaskDropsItsTargetAsIn116(GameTestHelper helper) {
		for (boolean dead : new boolean[] { false, true }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				Cow cow = scene.cow(COW_FROM_USER);
				LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(new ActionTarget(cow));
				EntityActionInstance stringAttack = scene.start("string_attack", ActionPhase.PERFORM);
				helper.assertTrue(stringAttack.getTaskTarget(helper.getLevel()).getEntity() == cow
						&& stringAttack.standRotationTarget != null && stringAttack.standRotationTarget.getEntity() == cow,
						"setup: the String Attack holds " + stringAttack.getTaskTarget(helper.getLevel()) + " and turns the Stand to "
								+ stringAttack.standRotationTarget + ", not the cow");
				String dropped;
				if (dead) {
					cow.setHealth(0.0F);
					cow.deathTime = 20;
					dropped = "a cow that has lain dead for 20 ticks";
				}
				else {
					Vec3 far = scene.user().position().add(10.0D, 0.0D, 0.0D);
					cow.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
					dropped = "a cow gone 10 blocks east";
				}
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				LivingComponentAction.getComponent(scene.stand()).tick();
				discardStrings(scene);
				scene.stand().updatePosition(scene.user());
				helper.assertTrue(stringAttack.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
						"setup: the String Attack still holds " + stringAttack.getTaskTarget(helper.getLevel()) + " after " + dropped);
				assertLooksAlongItsUser(helper, scene, "1.16 turned a following hierophant_green whose String Attack's task"
						+ " dropped " + dropped + " along its user's look south again (the action turns it to "
						+ stringAttack.standRotationTarget + ")");
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityTask.makeServerSideTask gave the task no target for a press target keepTarget refused, so the
	 * following Stand looked along its user's look (rotateStand): String Attack or Rapier Launch pressed at a cow 12 blocks
	 * east, past Action.checkRangeAndTarget's 8, or Rapier Launch at a block 14 blocks above its user, past its 10.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aFollowingStandDoesNotTurnToAPressTargetItsTaskRefusedAsIn116(GameTestHelper helper) {
		Object[][] rows = {
				{ "hierophant_green", "string_attack", false },
				{ "silver_chariot", "rapier_launch", false },
				{ "silver_chariot", "rapier_launch", true } };
		for (Object[] row : rows) {
			String name = (String) row[1];
			boolean atBlock = (boolean) row[2];
			StandTaskTargetOffsetGameTests.withStand(helper, (String) row[0], scene -> {
				BlockPos block = BlockPos.containing(scene.user().getEyePosition().add(0.0D, 14.0D, 0.0D));
				try {
					ActionTarget pressed;
					if (atBlock) {
						helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
						pressed = new ActionTarget(block, Direction.DOWN);
					}
					else {
						pressed = new ActionTarget(scene.cow(new Vec3(12.0D, 0.0D, 0.0D)));
					}
					LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(pressed);
					EntityActionInstance action = scene.set(name);
					helper.assertTrue(action.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
							"setup: the " + name + " pressed at " + pressed + " holds " + action.getTaskTarget(helper.getLevel()));
					StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
					scene.stand().updatePosition(scene.user());
					assertLooksAlongItsUser(helper, scene, "1.16 turned a following " + row[0] + " whose " + name + " was pressed at "
							+ (atBlock ? "a block 14 blocks above" : "a cow 12 blocks east") + " of its user, which its task refused,"
							+ " along its user's look south (the action turns it to " + action.standRotationTarget + ")");
				}
				finally {
					if (atBlock) {
						helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
					}
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.setTask gave a task whose press target was refused the previous task's target, and only that one
	 * turned the Stand: a String Attack pressed at a cow 12 blocks east takes over the near cow the String Attack before it
	 * held, and once that cow has lain dead for 20 ticks the following Stand looks along its user's look, not at the far cow
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandWhoseTaskTookOverATargetForARefusedPressLooksAlongItsUserOnceItDropsItAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			Cow near = scene.cow(COW_FROM_USER);
			Cow far = scene.cow(new Vec3(12.0D, 0.0D, 0.0D));
			LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(new ActionTarget(near));
			scene.start("string_attack", ActionPhase.PERFORM);
			discardStrings(scene);
			LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(new ActionTarget(far));
			EntityActionInstance next = scene.set("string_attack");
			helper.assertTrue(next.getTaskTarget(helper.getLevel()).getEntity() == near, "setup: the String Attack pressed at"
					+ " the far cow holds " + next.getTaskTarget(helper.getLevel()) + ", not the near cow the one before it held");
			near.setHealth(0.0F);
			near.deathTime = 20;
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
			LivingComponentAction.getComponent(scene.stand()).tick();
			discardStrings(scene);
			helper.assertTrue(next.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
					"setup: the String Attack still holds " + next.getTaskTarget(helper.getLevel()) + " after the near cow died");
			scene.stand().updatePosition(scene.user());
			assertLooksAlongItsUser(helper, scene, "1.16 turned a following hierophant_green whose String Attack, pressed at a"
					+ " cow past its range, dropped the near cow it took over along its user's look south (the action turns it to "
					+ next.standRotationTarget + ")");
		});
		helper.succeed();
	}

	/*
	 * 1.16 HierophantGreenBarrier.standPerform worked on task.getTarget(), and only the task's target turned the Stand
	 * (StandEntityTask.rotateStand): a Barrier pressed at a block 3 blocks east of its user faces it on the tick it places
	 * its barrier while its task holds the block, and looks along its user's look once its task dropped it (server code's
	 * StandEntity.setTaskTarget(null)); the port still places that barrier.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void barrierTurnsTheStandOnlyToTheBlockItsTaskHoldsAsItPlacesItsBarrierAsIn116(GameTestHelper helper) {
		for (boolean dropped : new boolean[] { false, true }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				BlockPos block = BlockPos.containing(scene.user().getEyePosition().add(COW_FROM_USER));
				try {
					helper.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
					LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(block, Direction.WEST));
					EntityActionInstance barrier = scene.set("barrier");
					helper.assertTrue(block.equals(barrier.getTaskTarget(helper.getLevel()).getBlockPos()),
							"setup: the Barrier holds " + barrier.getTaskTarget(helper.getLevel()) + ", not the block");
					if (dropped) {
						scene.stand().setTaskTarget(null);
						helper.assertTrue(barrier.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
								"setup: the Barrier still holds " + barrier.getTaskTarget(helper.getLevel()));
					}
					StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
					// the action's own tick, before its Stand clears it, as StandEntity.tick turns the Stand by it
					barrier._tickAction();
					scene.spawned().addAll(helper.getLevel().getEntitiesOfClass(HGBarrierEntity.class,
							scene.user().getBoundingBox().inflate(16.0D)));
					helper.assertTrue(scene.stand() instanceof HierophantGreenEntity hierophant && hierophant.hasBarrierAttached()
							&& scene.stand().getCurStandAction() == barrier, "setup: the Barrier placed no barrier on its perform");
					scene.stand().updatePosition(scene.user());
					if (dropped) {
						assertLooksAlongItsUser(helper, scene, "1.16 turned a following hierophant_green whose Barrier's task had"
								+ " dropped the block before it placed its barrier along its user's look south (the action turns"
								+ " it to " + barrier.standRotationTarget + ")");
					}
					else {
						Vec3 toBlock = Vec3.atCenterOf(block).subtract(scene.stand().getEyePosition()).normalize();
						Vec3 look = scene.stand().getLookAngle();
						helper.assertTrue(look.dot(toBlock) > 0.9999D, "1.16 turned a following hierophant_green whose Barrier"
								+ " held the block east of its user to it as it placed its barrier: it looks along " + look
								+ " instead of " + toBlock);
					}
				}
				finally {
					LivingComponentAction.getComponent(scene.stand()).setAction(null, scene.user(), SyncType.NO_SYNC);
					helper.getLevel().setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityTask.tick dropped the target keepTarget no longer passed before the action ticked, on every tick of the
	 * task: on the tick a cow Crazy Diamond's Heal held went 12 blocks away, past its 8, the Heal no longer put the Stand in
	 * front toward it (CrazyDiamondHeal.getOffsetFromUser, offsetToTarget of the task's target), and the following Stand
	 * stood and looked as it does without a task
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void healDropsACowPastItsRangeBeforeItTicksAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "crazy_diamond", scene -> {
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
			scene.stand().updatePosition(scene.user());
			Vec3 idle = scene.stand().position().subtract(scene.user().position());
			Cow cow = scene.cow(COW_FROM_USER);
			cow.setHealth(cow.getMaxHealth() - 4.0F);
			LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(cow));
			EntityActionInstance heal = scene.start("heal", ActionPhase.PERFORM);
			scene.stand().updatePosition(scene.user());
			Vec3 healing = scene.stand().position().subtract(scene.user().position());
			helper.assertTrue(heal.getTaskTarget(helper.getLevel()).getEntity() == cow && healing.distanceTo(idle) > 0.1D,
					"setup: the Heal holds " + heal.getTaskTarget(helper.getLevel()) + " and puts the Stand at its user's feet + "
							+ healing + ", where it stands without a task");
			Vec3 far = scene.user().position().add(12.0D, 0.0D, 0.0D);
			cow.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
			LivingComponentAction.getComponent(scene.stand()).tick();
			helper.assertTrue(heal.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY
					&& scene.stand().getCurStandAction() == heal, "setup: the Heal still holds "
							+ heal.getTaskTarget(helper.getLevel()) + " after the cow went 12 blocks east, or it ended");
			scene.stand().updatePosition(scene.user());
			Vec3 placed = scene.stand().position().subtract(scene.user().position());
			helper.assertTrue(placed.distanceTo(idle) < EPS, "1.16 put a following crazy_diamond whose Heal dropped a cow 12"
					+ " blocks east of its user who looks south where it stands without a task, its user's feet + " + idle
					+ ", on that tick; it is at + " + placed);
			assertLooksAlongItsUser(helper, scene, "1.16 turned a following crazy_diamond whose Heal dropped the cow along its"
					+ " user's look south (the action turns it to " + heal.standRotationTarget + ")");
		});
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityAction.rotateStandTowardsTarget turned the Stand to target.getTargetPos(true), the eyes of any entity
	 * its task held: Crazy Diamond's Revert (CrazyDiamondPreviousState, which kept primed TNT and a TNT minecart) faced their
	 * eyes, low in their box, and not the middle of the box
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void revertFacesTheEyesOfTheTntItHoldsAsIn116(GameTestHelper helper) {
		for (EntityType<?> type : List.<EntityType<?>>of(EntityType.TNT, EntityType.TNT_MINECART)) {
			StandTaskTargetOffsetGameTests.withStand(helper, "crazy_diamond", scene -> {
				scene.power().setResolveLevel(3);
				Entity tnt = type.create(helper.getLevel());
				helper.assertTrue(tnt != null, "Could not make a " + type);
				if (tnt instanceof PrimedTnt primed) {
					primed.setFuse(1000);
				}
				tnt.setNoGravity(true);
				Vec3 pos = scene.user().position().add(2.5D, 0.0D, 0.0D);
				tnt.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
				helper.assertTrue(helper.getLevel().addFreshEntity(tnt), "Could not add a " + type);
				scene.spawned().add(tnt);
				LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(tnt));
				EntityActionInstance revert = scene.set("revert_state");
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				helper.assertTrue(revert.getTaskTarget(helper.getLevel()).getEntity() == tnt
						&& Math.abs(tnt.getEyeY() - tnt.getBoundingBox().getCenter().y) > 0.2D,
						"setup: the Revert holds " + revert.getTaskTarget(helper.getLevel()) + ", not the " + type
								+ ", or its eyes are at the middle of its box");
				scene.stand().updatePosition(scene.user());
				Vec3 toEyes = tnt.getEyePosition().subtract(scene.stand().getEyePosition()).normalize();
				Vec3 look = scene.stand().getLookAngle();
				helper.assertTrue(look.dot(toEyes) > 0.9999D, "1.16 turned a crazy_diamond whose Revert held a " + type
						+ " 2.5 blocks east of its user to its eyes " + tnt.getEyePosition() + ": it looks along " + look
						+ " instead of " + toEyes);
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 GoldExperienceHeavyPunch.rotateStandTowardsTarget overrode that point: the Stand faced the top of the box of the
	 * entity its task held, not its eyes
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void goldExperiencesHeavyPunchFacesTheTopOfTheCowItHoldsAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "gold_experience", scene -> {
			Cow cow = scene.cow(new Vec3(2.0D, 0.0D, 0.0D));
			LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(cow));
			EntityActionInstance heavyPunch = scene.set("heavy_punch");
			StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
			helper.assertTrue(heavyPunch.getTaskTarget(helper.getLevel()).getEntity() == cow,
					"setup: the heavy punch holds " + heavyPunch.getTaskTarget(helper.getLevel()) + ", not the cow");
			scene.stand().updatePosition(scene.user());
			Vec3 top = new Vec3(cow.getX(), cow.getBoundingBox().maxY, cow.getZ());
			Vec3 toTop = top.subtract(scene.stand().getEyePosition()).normalize();
			Vec3 look = scene.stand().getLookAngle();
			helper.assertTrue(look.dot(toTop) > 0.99999D, "1.16 turned a gold_experience whose heavy punch held a cow 2 blocks"
					+ " east of its user to the top of the cow's box " + top + ": it looks along " + look + " instead of " + toTop);
		});
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.setTaskTarget was public, and server code dropped the target of another Stand's task with it (RHCP's
	 * counterattack, setTaskTarget(new ActionTarget(null)) on the Stand it hit): the Stand turned along its user's look
	 * again and tracking clients got the emptied target (TrStandTaskTargetPacket). So for a String Attack, a punch, a heavy
	 * punch and Crazy Diamond's Heal, each holding a cow, whose task target the punches keep as their strike's.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void serverCodeDropsTheTaskTargetOfAStandsActionAsIn116(GameTestHelper helper) {
		Object[][] rows = {
				{ "hierophant_green", "string_attack", ActionPhase.PERFORM, COW_FROM_USER },
				{ "star_platinum", "punch", ActionPhase.WINDUP, new Vec3(1.5D, 0.0D, 0.0D) },
				{ "star_platinum", "heavy_punch", ActionPhase.WINDUP, new Vec3(1.5D, 0.0D, 0.0D) },
				{ "crazy_diamond", "heal", ActionPhase.PERFORM, COW_FROM_USER } };
		for (Object[] row : rows) {
			String name = (String) row[1];
			StandTaskTargetOffsetGameTests.withStand(helper, (String) row[0], scene -> {
				Cow cow = scene.cow((Vec3) row[3]);
				cow.setHealth(cow.getMaxHealth() - 4.0F);
				LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(cow));
				LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(new ActionTarget(cow));
				EntityActionInstance action = scene.start(name, (ActionPhase) row[2]);
				discardStrings(scene);
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == cow,
						"setup: the " + name + " holds " + action.getTaskTarget(helper.getLevel()) + ", not the cow");
				List<TrEntityActionTargetPacket> sent = setTaskTargetWatched(helper, scene, action, null);
				helper.assertTrue(action.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
						"1.16 StandEntity.setTaskTarget dropped the target of a " + row[0] + "'s " + name + "; it holds "
								+ action.getTaskTarget(helper.getLevel()));
				helper.assertTrue(sent.size() == 1 && isNone(sent.get(0).taskTarget()) && isNone(sent.get(0).rotationTarget()),
						"1.16 sent tracking clients the dropped task target of a " + row[0] + "'s " + name + " once; they got " + sent);
				scene.stand().updatePosition(scene.user());
				assertLooksAlongItsUser(helper, scene, "1.16 turned a following " + row[0] + " whose " + name
						+ " no longer held the cow along its user's look south (the action turns it to " + action.standRotationTarget + ")");
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityTask.setTarget, through StandEntity.setTaskTarget: a target the action keeps becomes its task's target,
	 * which the Stand turns to and tracking clients get; one it does not keep, the Stand itself (keepTarget), leaves it the
	 * one it held and sends nothing. A String Attack and Crazy Diamond's Heal holding one cow are given another, and a
	 * client's copy takes it from what was sent.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void serverCodeGivesAStandsActionATaskTargetItKeepsAsIn116(GameTestHelper helper) {
		Object[][] rows = { { "hierophant_green", "string_attack" }, { "crazy_diamond", "heal" } };
		for (Object[] row : rows) {
			String name = (String) row[1];
			StandTaskTargetOffsetGameTests.withStand(helper, (String) row[0], scene -> {
				Cow first = scene.cow(COW_FROM_USER);
				Cow other = scene.cow(new Vec3(-3.0D, 0.0D, 1.0D));
				for (Cow cow : new Cow[] { first, other }) {
					cow.setHealth(cow.getMaxHealth() - 4.0F);
				}
				LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(first));
				LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(new ActionTarget(first));
				EntityActionInstance action = scene.start(name, ActionPhase.PERFORM);
				discardStrings(scene);
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == first,
						"setup: the " + name + " holds " + action.getTaskTarget(helper.getLevel()) + ", not the first cow");
				List<TrEntityActionTargetPacket> sent = setTaskTargetWatched(helper, scene, action, new ActionTarget(other));
				helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == other && sent.size() == 1
						&& sent.get(0).taskTarget() != null && sent.get(0).taskTarget().sameTarget(new ActionTarget(other)),
						"1.16 StandEntity.setTaskTarget gave a " + row[0] + "'s " + name + " the other cow and sent it to tracking"
								+ " clients; it holds " + action.getTaskTarget(helper.getLevel()) + " and sent " + sent);
				scene.stand().updatePosition(scene.user());
				Vec3 toOther = other.getEyePosition().subtract(scene.stand().getEyePosition()).normalize();
				Vec3 look = scene.stand().getLookAngle();
				helper.assertTrue(look.dot(toOther) > 0.9999D, "1.16 turned a " + row[0] + " whose " + name + " was given the"
						+ " other cow to it: it looks along " + look + " instead of " + toOther);

				List<TrEntityActionTargetPacket> refused = setTaskTargetWatched(helper, scene, action, new ActionTarget(scene.stand()));
				helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == other && refused.isEmpty(),
						"1.16 StandEntityTask.setTarget left a " + row[0] + "'s " + name + " given its own Stand the cow it held;"
								+ " it holds " + action.getTaskTarget(helper.getLevel()) + " and sent " + refused);

				EntityActionInstance client = name.equals("heal") ? new ClientHeal(action.ability)
						: new ClientStringAttack(action.ability);
				LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
				actions.setAction(null, scene.user(), SyncType.NO_SYNC);
				actions.setAction(client, scene.user(), SyncType.NO_SYNC);
				TrEntityActionTargetPacket got = sent.get(0);
				client.setTargetsFromServer(got.keptTarget(), got.rotationTarget(), got.aimAs(), got.taskTarget());
				helper.assertTrue(client.getTaskTarget(helper.getLevel()).getEntity() == other, "A client's " + name + " given what"
						+ " the server sent holds " + client.getTaskTarget(helper.getLevel()) + ", not the other cow");
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityTask.rotateStand turned the Stand and its head to its task's target (MCUtil.rotateTowards set
	 * yHeadRot), and an action that then gave the Stand its user's yaw, as Shadow the World's StunComboAttack.moveStandForward
	 * did with setRot, left the head on the target: on the server, and on a client turning by the target its server sent. A
	 * Stand whose task held no target got its head on its yaw (StandEntity.defaultRotation), not its user's head yaw.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theHeadStaysOnTheTaskTargetWhenTheActionThenTurnsTheStandsYawAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "server", "client", "no target" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				Cow cow = scene.cow(COW_FROM_USER);
				YawToUserProbe probe = new YawToUserProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
						new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "yaw_to_user_probe"));
				LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
				EntityActionInstance action = row.equals("client") ? new ClientProbeAction(probe)
						: probe.initActionOnAbilityUse(helper.getLevel(), scene.user(), scene.stand(), null);
				actions.setAction(action, scene.user(), SyncType.NO_SYNC);
				if (row.equals("server")) {
					scene.stand().setTaskTarget(new ActionTarget(cow));
				}
				else if (row.equals("client")) {
					action.setTargetsFromServer(null, null, AimingEntity.STAND, new ActionTarget(cow));
				}
				boolean held = !row.equals("no target");
				helper.assertTrue(scene.stand().getCurStandAction() == action
						&& (action.getTaskTarget(helper.getLevel()).getEntity() == cow) == held,
						"setup: the " + row + " probe holds " + action.getTaskTarget(helper.getLevel()));
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				scene.user().setYHeadRot(VIEW_YAW + 30.0F);
				scene.stand().tick();
				scene.stand().tick();
				StandEntity stand = scene.stand();
				helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - VIEW_YAW)) < EPS
						&& Math.abs(Mth.wrapDegrees(stand.yBodyRot - VIEW_YAW)) < EPS, "setup: the " + row + " probe's Stand has"
								+ " yaw " + stand.getYRot() + " and body yaw " + stand.yBodyRot + ", not its user's " + VIEW_YAW);
				Vec3 toCow = cow.getEyePosition().subtract(stand.getEyePosition());
				float headYaw = held ? (float) (Mth.atan2(toCow.z, toCow.x) * Mth.RAD_TO_DEG) - 90.0F : VIEW_YAW;
				helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yHeadRot - headYaw)) < 1.0E-3F
						&& Math.abs(Mth.wrapDegrees(stand.yHeadRotO - headYaw)) < 1.0E-3F, (held
								? "1.16 left the head of a hierophant_green whose action turned it to its user's yaw after its task's"
										+ " turn (" + row + ") on the cow 3 blocks east"
								: "1.16 put the head of a hierophant_green whose task held no target on its yaw, not its user's"
										+ " head yaw")
								+ ", at yaw " + headYaw + " on both ticks: it is at " + stand.yHeadRot + ", " + stand.yHeadRotO
								+ " before");
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 PowerBaseImpl.checkTarget emptied a target GoldExperienceBoneMeal.checkTarget refused, an adult cow, so a player's
	 * press at it failed, and a Bone Meal started at it anyway (a mob's press, whose aim moved after its check) held no
	 * target: the following Stand looked along its user's look. A calf, which it accepts, turns the Stand to it.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void goldExperiencesBoneMealDoesNotTurnTheStandToATargetItsCheckRefusesAsIn116(GameTestHelper helper) {
		for (boolean calf : new boolean[] { false, true }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "gold_experience", scene -> {
				Cow cow = scene.cow(COW_FROM_USER);
				cow.setBaby(calf);
				LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(cow));
				EntityActionInstance boneMeal = scene.set("bone_meal");
				String what = calf ? "a calf" : "an adult cow";
				helper.assertTrue((boneMeal.getTaskTarget(helper.getLevel()).getEntity() == cow) == calf,
						"setup: the Bone Meal pressed at " + what + " holds " + boneMeal.getTaskTarget(helper.getLevel()));
				helper.assertTrue(calf || !scene.power().getAbility("bone_meal").checkSpecificConditions(scene.power()).isPositive(),
						"1.16 refused a player's Bone Meal pressed at an adult cow (GoldExperienceBoneMeal.checkTarget)");
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW);
				scene.stand().updatePosition(scene.user());
				if (calf) {
					Vec3 toEyes = cow.getEyePosition().subtract(scene.stand().getEyePosition()).normalize();
					Vec3 look = scene.stand().getLookAngle();
					helper.assertTrue(look.dot(toEyes) > 0.9999D, "1.16 turned a gold_experience whose Bone Meal held a calf 3"
							+ " blocks east of its user to its eyes: it looks along " + look + " instead of " + toEyes);
				}
				else {
					assertLooksAlongItsUser(helper, scene, "1.16 turned a following gold_experience whose Bone Meal was started at an"
							+ " adult cow, which GoldExperienceBoneMeal.checkTarget refused, along its user's look south (the action"
							+ " turns it to " + boneMeal.standRotationTarget + ")");
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityMeleeBarrage.checkStandTarget: a barrage's task kept an entity only while it was the Stand's barrage
	 * clash opponent, whether the barrage was pressed at it (makeServerSideTask) or a hit met it (StandEntity.punch gave the
	 * task each hit's target first); pressed at the opposing Stand before the clash, the task held nothing. The Stand then
	 * faced it and stood in front toward it (StandEntity.taskOffset) wherever its user looked, until the clash ended and
	 * StandEntityTask.tick dropped it.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aBarrageKeepsItsBarrageClashOpponentAsItsTaskTargetAsIn116(GameTestHelper helper) {
		for (boolean pressedInClash : new boolean[] { true, false }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
				StandPower rivalPower = null;
				LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
				try {
					Vec3 rivalPos = scene.user().position().add(0.0D, 0.0D, 4.0D);
					rival.setNoGravity(true);
					rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
					rival.setHealth(500.0F);
					rival.moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
					helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
					rivalPower = PowerClass.STAND.attachGet(rival);
					helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED && type.summon(rival, rivalPower),
							"Could not give the rival Star Platinum");
					StandEntity opponent = rivalPower.getSummonedStandEntity();
					Vec3 opponentPos = scene.user().position().add(0.0D, 0.0D, 2.0D);
					opponent.setNoGravity(true);
					opponent.moveTo(opponentPos.x, opponentPos.y, opponentPos.z, 180.0F, 0.0F);
					scene.user().lookAt(EntityAnchorArgument.Anchor.EYES, opponent.getBoundingBox().getCenter());
					LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(opponent));
					if (pressedInClash) {
						startBarrageClash(helper, scene.stand(), opponent);
					}
					EntityActionInstance barrage = scene.set("barrage");
					helper.assertTrue(StandEntityBarrageAbility.clipDirectionalBarrageTarget(scene.stand(), barrage, 1.0F)
							.getMainEntity() == opponent, "setup: the barrage does not aim at the opposing Stand its user looks at");
					String how;
					if (pressedInClash) {
						how = "pressed at its clash opponent";
					}
					else {
						helper.assertTrue(barrage.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
								"1.16 StandEntityMeleeBarrage.checkStandTarget refused a barrage pressed at an opposing Stand before"
										+ " their barrage clash; its task holds " + barrage.getTaskTarget(helper.getLevel()));
						startBarrageClash(helper, scene.stand(), opponent);
						for (int tick = 0; tick < 10 && (barrage.punchedTarget == null
								|| barrage.punchedTarget.getMainEntity() != opponent); tick++) {
							scene.stand().tickCount++;
							actions.tick();
						}
						helper.assertTrue(barrage.punchedTarget != null && barrage.punchedTarget.getMainEntity() == opponent,
								"setup: no hit of the barrage met the opposing Stand: " + barrage.punchedTarget);
						how = "whose hit met its clash opponent";
					}
					helper.assertTrue(barrage.getTaskTarget(helper.getLevel()).getEntity() == opponent, "1.16 kept the"
							+ " barrage clash opponent of a star_platinum's barrage " + how + " as its task's target; it holds "
							+ barrage.getTaskTarget(helper.getLevel()));

					StandTaskTargetOffsetGameTests.turn(scene.user(), -90.0F, -90.0F);
					scene.stand().updatePosition(scene.user());
					Vec3 toEyes = opponent.getEyePosition().subtract(scene.stand().getEyePosition()).normalize();
					helper.assertTrue(scene.stand().getLookAngle().dot(toEyes) > 0.9999D, "1.16 turned a star_platinum whose"
							+ " barrage held its clash opponent to it while its user looks east: it looks along "
							+ scene.stand().getLookAngle() + " instead of " + toEyes);
					Vec3 placed = scene.stand().position().subtract(scene.user().position()).multiply(1.0D, 0.0D, 1.0D);
					Vec3 toOpponent = opponent.position().subtract(scene.user().position()).multiply(1.0D, 0.0D, 1.0D);
					helper.assertTrue(placed.lengthSqr() > 0.01D && placed.normalize().dot(toOpponent.normalize()) > 0.999D,
							"1.16 put a star_platinum whose barrage held its clash opponent in front toward it while its user"
									+ " looks east (StandEntity.taskOffset): it is at its user's feet + " + placed);

					scene.stand().barrageClashStopped();
					scene.stand().tickCount++;
					actions.tick();
					helper.assertTrue(barrage.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
							"1.16 dropped the opposing Stand from a barrage's task once their barrage clash ended; it holds "
									+ barrage.getTaskTarget(helper.getLevel()));
				}
				finally {
					actions.setAction(null, scene.user(), SyncType.NO_SYNC);
					scene.stand().barrageClashStopped();
					if (rivalPower != null && rivalPower.isSummoned()) {
						type.forceUnsummon(rival, rivalPower);
					}
					rival.discard();
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.clearTask ran BarrageHandler.reset on every change of a Stand's task, before the old task's
	 * onTaskStopped: the Stand's own clash opponent was emptied with its synced id left as it was, and the other Stand was
	 * not told. StandEntityMeleeBarrage.onTaskStopped then stopped the clash only for another action or none (newAction !=
	 * this), finding no opponent left, so it synced the end for this Stand alone. So the same barrage set again held no
	 * clash: its task dropped the opponent by its first tick (checkStandTarget), and since the field changed only with the
	 * synced id (onSyncedDataUpdated), the opponent's later parries did not bring it back. The opponent kept its side.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void anActionChangeDuringABarrageClashDropsOnlyThatStandsSideAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "pressed at the opponent", "pressed at nothing", "a punch", "the end" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
				StandPower rivalPower = null;
				LivingComponentAction actions = LivingComponentAction.getComponent(scene.stand());
				try {
					Vec3 rivalPos = scene.user().position().add(0.0D, 0.0D, 4.0D);
					rival.setNoGravity(true);
					rival.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
					rival.setHealth(500.0F);
					rival.moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
					helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
					rivalPower = PowerClass.STAND.attachGet(rival);
					helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED && type.summon(rival, rivalPower),
							"Could not give the rival Star Platinum");
					StandEntity opponent = rivalPower.getSummonedStandEntity();
					Vec3 opponentPos = scene.user().position().add(0.0D, 0.0D, 2.0D);
					opponent.setNoGravity(true);
					opponent.moveTo(opponentPos.x, opponentPos.y, opponentPos.z, 180.0F, 0.0F);
					scene.user().lookAt(EntityAnchorArgument.Anchor.EYES, opponent.getBoundingBox().getCenter());
					LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(new ActionTarget(opponent));
					startBarrageClash(helper, scene.stand(), opponent);
					EntityActionInstance first = scene.set("barrage");
					helper.assertTrue(first.getTaskTarget(helper.getLevel()).getEntity() == opponent,
							"setup: the barrage pressed at its clash opponent holds " + first.getTaskTarget(helper.getLevel()));
					String what = "a star_platinum whose barrage in a barrage clash was followed by " + row;
					boolean sameBarrage = row.startsWith("pressed");
					EntityActionInstance again = null;
					switch (row) {
						case "a punch" -> scene.set("punch");
						case "the end" -> actions.setAction(null, scene.user(), SyncType.NO_SYNC);
						default -> {
							if (row.equals("pressed at nothing")) {
								LivingComponentAction.getComponent(scene.user()).entityAim.setTarget(ActionTarget.EMPTY);
								LivingComponentAction.getComponent(scene.stand()).entityAim.setTarget(ActionTarget.EMPTY);
							}
							again = scene.set("barrage");
						}
					}
					helper.assertTrue(scene.stand().barrageClashOpponent().isEmpty()
							&& opponent.barrageClashOpponent().orElse(null) == scene.stand(), "1.16 dropped only the clash side of "
									+ what + " (BarrageHandler.reset), the opponent kept pointing at it; the clash opponents are "
									+ scene.stand().barrageClashOpponent() + " and " + opponent.barrageClashOpponent());
					int syncedId = syncedClashOpponentId(scene.stand());
					int expectedId = sameBarrage ? opponent.getId() : -1;
					helper.assertTrue(syncedId == expectedId, "1.16 " + (sameBarrage ? "left the synced clash opponent of " + what
							+ " as it was, so its clients kept it" : "synced the end of the clash of " + what) + ": the synced id is "
									+ syncedId + ", not " + expectedId);
					if (again != null) {
						scene.stand().tickCount++;
						actions.tick();
						helper.assertTrue(again.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
								"1.16 StandEntityMeleeBarrage.checkStandTarget left the task of " + what + " no clash opponent to"
										+ " hold after its first tick; it holds " + again.getTaskTarget(helper.getLevel()));
						// the opponent parries this barrage's hits again
						setBarrageClashOpponent(opponent, scene.stand());
						helper.assertTrue(scene.stand().barrageClashOpponent().isEmpty()
								&& opponent.barrageClashOpponent().orElse(null) == scene.stand(), "1.16 assigned the clash"
										+ " opponent only as its synced id changed, so the opponent's next parry did not give " + what
										+ " its clash back; the clash opponents are " + scene.stand().barrageClashOpponent() + " and "
										+ opponent.barrageClashOpponent());
					}
				}
				finally {
					actions.setAction(null, scene.user(), SyncType.NO_SYNC);
					scene.stand().barrageClashStopped();
					if (rivalPower != null && rivalPower.isSummoned()) {
						type.forceUnsummon(rival, rivalPower);
					}
					rival.discard();
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.tickHeadTurn gave the body of a Stand with a task the yaw it started each tick with: an action whose
	 * tick turned the Stand (setRot) showed its body along that yaw on the next tick, while StandEntityTask.rotateStand put
	 * the yaw and head back on the user's look (defaultRotation) or on the task's target (MCUtil.rotateTowards), neither of
	 * which moved the body. A Stand with no task kept the vanilla head turn: left where it is, its body stays.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandsBodyTakesTheYawItsActionLeftAsTheNextTickStartsAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "no target", "target", "no task" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				StandEntity stand = scene.stand();
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW, VIEW_YAW + 50.0F);
				if (row.equals("no task")) {
					stand.setCanFollowUser(false);
					// past its summon pose, which took the yaw as the body (1.16 StandEntity.tickHeadTurn), as its first task ended it
					stand.endSummonPose();
					stand.setYRot(VIEW_YAW);
					stand.yBodyRot = VIEW_YAW + 30.0F;
					// at rest since the last tick, as the level marks it before ticking it
					stand.setOldPosAndRot();
					stand.tick();
					helper.assertTrue(stand.getCurStandAction() == null
							&& Math.abs(Mth.wrapDegrees(stand.yBodyRot - VIEW_YAW - 30.0F)) < EPS, "1.16 left the body of a"
									+ " hierophant_green with no task, standing where it is, to the vanilla head turn, at "
									+ (VIEW_YAW + 30.0F) + ": it is at " + stand.yBodyRot);
					return;
				}
				YawAsideProbe probe = new YawAsideProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
						new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "yaw_aside_probe"));
				EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
				LivingComponentAction actions = LivingComponentAction.getComponent(stand);
				stand.summonLockTicks = 0;
				actions.setAction(action, scene.user(), SyncType.NO_SYNC);
				Cow cow = row.equals("target") ? scene.cow(COW_FROM_USER) : null;
				if (cow != null) {
					stand.setTaskTarget(new ActionTarget(cow));
				}
				ActionTarget held = action.getTaskTarget(helper.getLevel());
				helper.assertTrue(stand.getCurStandAction() == action && (cow != null ? held.getEntity() == cow
						: held.getType() == ActionTarget.TargetType.EMPTY), "setup: the probe holds " + held);
				// StandEntity.tick, then its action's tick (EntityTickEvent.Post)
				for (int tick = 0; tick < 2; tick++) {
					stand.tick();
					actions.tick();
				}
				helper.assertTrue(stand.getCurStandAction() == action && Math.abs(Mth.wrapDegrees(stand.getYRot()
						- VIEW_YAW - YawAsideAction.ASIDE)) < EPS, "setup: the probe's tick left the Stand at yaw " + stand.getYRot());
				float headYaw = VIEW_YAW;
				if (cow != null) {
					Vec3 toCow = cow.getEyePosition().subtract(stand.getEyePosition());
					headYaw = (float) (Mth.atan2(toCow.z, toCow.x) * Mth.RAD_TO_DEG) - 90.0F;
				}
				helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yBodyRot - VIEW_YAW - YawAsideAction.ASIDE)) < EPS
						&& Math.abs(Mth.wrapDegrees(stand.yHeadRot - headYaw)) < 1.0E-3F, "1.16 turned the body of a following"
								+ " hierophant_green whose action's tick turned it " + YawAsideAction.ASIDE + " degrees off its user's"
								+ " look to that yaw as the next tick started, and its head " + (cow != null ? "to the cow its task"
										+ " holds" : "along its user's look") + " at " + headYaw + ": the body is at " + stand.yBodyRot
								+ ", the head at " + stand.yHeadRot);
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityAction.rotateStand: a Stand whose action moved it by itself (standMovesByItself,
	 * noAdheringToUserOffset) and whose task held no target kept its yaw, pitch and head as they were, where
	 * StandEntity.defaultRotation would have put them on its user's look and the head on the yaw; under manual control
	 * defaultRotation still ran and put the head on the yaw.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandItsActionMovesByItselfKeepsItsRotationAndHeadWithoutATaskTargetAsIn116(GameTestHelper helper) {
		for (boolean manual : new boolean[] { false, true }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				StandEntity stand = scene.stand();
				SelfMovingProbe probe = new SelfMovingProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
						new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "self_moving_probe"));
				EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
				stand.summonLockTicks = 0;
				LivingComponentAction.getComponent(stand).setAction(action, scene.user(), SyncType.NO_SYNC);
				if (manual) {
					stand.setManuallyControlled(true);
				}
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW + 60.0F, VIEW_YAW + 60.0F);
				stand.setYRot(VIEW_YAW - 20.0F);
				stand.setXRot(10.0F);
				stand.setYHeadRot(VIEW_YAW + 10.0F);
				stand.yHeadRotO = VIEW_YAW + 10.0F;
				helper.assertTrue(stand.getCurStandAction() == action && !stand.isFollowingUser()
						&& stand.isManuallyControlled() == manual
						&& action.getTaskTarget(helper.getLevel()).getType() == ActionTarget.TargetType.EMPTY,
						"setup: the probe does not move its Stand by itself without a task target, manual control " + manual);
				stand.tick();
				float headYaw = manual ? VIEW_YAW - 20.0F : VIEW_YAW + 10.0F;
				helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - VIEW_YAW + 20.0F)) < EPS
						&& Math.abs(stand.getXRot() - 10.0F) < EPS
						&& Math.abs(Mth.wrapDegrees(stand.yHeadRot - headYaw)) < EPS
						&& Math.abs(Mth.wrapDegrees(stand.yHeadRotO - headYaw)) < EPS, "1.16 left a hierophant_green its action"
								+ " moves by itself" + (manual ? ", manually controlled," : "") + " at yaw " + (VIEW_YAW - 20.0F)
								+ " and pitch 10 with its head at " + headYaw + ", its user looking at " + (VIEW_YAW + 60.0F)
								+ ": yaw " + stand.getYRot() + ", pitch " + stand.getXRot() + ", head " + stand.yHeadRot + " from "
								+ stand.yHeadRotO);
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.tick ended a barrage clash, on both Stands, once the opponent was out of the Stand's reach
	 * (isTargetInReach: JojoModUtil.getDistance from the Stand's eyes to where the line toward the opponent's box enters it,
	 * less half the Stand's width, against REACH_DISTANCE, 2.5 for Star Platinum), not its max range: an opponent 2.8
	 * blocks away, past the max range box to box, stays; one 3.5 away or 2.9 away and 1.5 higher ends it.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aBarrageClashEndsOnceTheOpponentLeavesTheStandsReachAsIn116(GameTestHelper helper) {
		double[][] rows = { { 2.8D, 0.0D, 0.0D }, { 3.5D, 0.0D, 1.0D }, { 2.9D, 1.5D, 1.0D } };
		for (double[] row : rows) {
			boolean ends = row[2] > 0.0D;
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
				StandPower rivalPower = null;
				StandEntity stand = scene.stand();
				try {
					Vec3 rivalPos = scene.user().position().add(0.0D, 0.0D, 6.0D);
					rival.setNoGravity(true);
					rival.moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
					helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
					rivalPower = PowerClass.STAND.attachGet(rival);
					helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED && type.summon(rival, rivalPower),
							"Could not give the rival Star Platinum");
					StandEntity opponent = rivalPower.getSummonedStandEntity();
					stand.setCanFollowUser(false);
					stand.setNoGravity(true);
					opponent.setNoGravity(true);
					Vec3 at = stand.position().add(0.0D, row[1], row[0]);
					opponent.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
					startBarrageClash(helper, stand, opponent);
					double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
					double fromEyes = ActionTargetRange.getDistance(stand, opponent.getBoundingBox());
					double boxToBox = MathUtil.getAABBDistanceDetailed(stand.getBoundingBox(), opponent.getBoundingBox()).distance();
					helper.assertTrue((fromEyes > reach) == ends && (ends || boxToBox > stand.getMaxRange())
							&& (row[1] == 0.0D || boxToBox <= reach),
							"setup: the opponent " + row[0] + " south and " + row[1] + " up is " + fromEyes + " from the Stand's eyes and "
									+ boxToBox + " box to box, the reach " + reach + ", the max range " + stand.getMaxRange());
					stand.tick();
					String what = "a star_platinum's barrage clash with an opponent " + row[0] + " blocks south and " + row[1]
							+ " up, " + fromEyes + " from its eyes, with a reach of " + reach;
					if (ends) {
						helper.assertTrue(stand.barrageClashOpponent().isEmpty() && opponent.barrageClashOpponent().isEmpty(),
								"1.16 ended " + what + " on both Stands; the clash opponents are " + stand.barrageClashOpponent()
										+ " and " + opponent.barrageClashOpponent());
					}
					else {
						helper.assertTrue(stand.barrageClashOpponent().orElse(null) == opponent
								&& opponent.barrageClashOpponent().orElse(null) == stand, "1.16 kept " + what
										+ "; the clash opponents are " + stand.barrageClashOpponent() + " and "
										+ opponent.barrageClashOpponent());
					}
				}
				finally {
					stand.barrageClashStopped();
					if (rivalPower != null && rivalPower.isSummoned()) {
						type.forceUnsummon(rival, rivalPower);
					}
					rival.discard();
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.defaultRotation gave any Stand neither manually controlled nor remote-fixed its user's yaw and pitch,
	 * so a Stand being retracted looked along its user's look as it flew back; a remote-fixed one kept its rotation, one its
	 * action moved by itself too (StandEntityAction.rotateStand), and one whose task held a target faced it.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandBeingRetractedLooksAlongItsUsersLookAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "retracted", "remote-fixed", "moved by its action", "holding a target" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
				StandEntity stand = scene.stand();
				Cow cow = null;
				if (row.equals("moved by its action") || row.equals("holding a target")) {
					StandEntityAbility probe = row.equals("moved by its action")
							? new SelfMovingProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
									new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "self_moving_probe"))
							: new YawAsideProbe(ModStandAbilities.HG_STRING_ATTACK.get(),
									new AbilityId(PowerClass.STAND, JojoMod.resLoc("hierophant_green"), "yaw_aside_probe"));
					EntityActionInstance action = probe.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
					LivingComponentAction.getComponent(stand).setAction(action, scene.user(), SyncType.NO_SYNC);
					if (row.equals("holding a target")) {
						cow = scene.cow(COW_FROM_USER);
						stand.setTaskTarget(new ActionTarget(cow));
						helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == cow,
								"setup: the probe holds " + action.getTaskTarget(helper.getLevel()));
					}
				}
				Vec3 away = scene.user().position().add(-5.0D, 0.0D, 0.0D);
				stand.moveTo(away.x, away.y, away.z, VIEW_YAW - 40.0F, 20.0F);
				if (row.equals("remote-fixed")) {
					stand.setCanFollowUser(false);
				}
				stand.retract();
				StandTaskTargetOffsetGameTests.turn(scene.user(), VIEW_YAW + 60.0F, VIEW_YAW + 60.0F);
				scene.user().setXRot(10.0F);
				helper.assertTrue(stand.isBeingRetracted() && !stand.isManuallyControlled() && !stand.isFollowingUser(),
						"setup: the " + row + " hierophant_green is not being retracted");
				stand.updatePosition(scene.user());
				String what = "a hierophant_green being retracted, " + row + ", whose user looks at yaw " + (VIEW_YAW + 60.0F)
						+ ", pitch 10";
				switch (row) {
					case "retracted" -> assertLooksAlongItsUser(helper, scene, "1.16 turned " + what + " along its user's look");
					case "holding a target" -> {
						Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition()).normalize();
						helper.assertTrue(stand.getLookAngle().dot(toEyes) > 0.9999D, "1.16 turned " + what + " to the cow its task"
								+ " holds: it looks along " + stand.getLookAngle() + " instead of " + toEyes);
					}
					default -> helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - VIEW_YAW + 40.0F)) < EPS
							&& Math.abs(stand.getXRot() - 20.0F) < EPS, "1.16 left " + what + " at yaw " + (VIEW_YAW - 40.0F)
									+ ", pitch 20: yaw " + stand.getYRot() + ", pitch " + stand.getXRot());
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 MCUtil.rotateTowards, the turn to a target, set the yaw, the pitch and the head and never the body, with a task
	 * or without one. StandEntity.lookAtPos(pos, true) turns a Stand with no action that way too, as add-ons call it (RHCP
	 * facing its lock under manual control, Wonder of U's aim).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aFullTurnToAPositionLeavesTheBodyOfAStandWithNoActionAsIn116(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "hierophant_green", scene -> {
			StandEntity stand = scene.stand();
			stand.setCanFollowUser(false);
			stand.setYRot(VIEW_YAW);
			stand.setXRot(0.0F);
			stand.setYHeadRot(VIEW_YAW);
			stand.yBodyRot = VIEW_YAW + 30.0F;
			Cow cow = scene.cow(COW_FROM_USER.add(0.0D, 1.5D, 0.0D));
			Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
			float yaw = MathUtil.yRotDegFromVec(toEyes);
			float pitch = MathUtil.xRotDegFromVec(toEyes);
			helper.assertTrue(stand.getCurStandAction() == null && Math.abs(Mth.wrapDegrees(yaw - VIEW_YAW - 30.0F)) > 1.0F
					&& Math.abs(pitch) > 1.0F, "setup: the Stand has an action, or the cow's eyes lie along its body or level"
							+ " with its eyes: yaw " + yaw + ", pitch " + pitch);
			helper.assertTrue(stand.lookAtPos(cow.getEyePosition(), true), "setup: the Stand did not turn to the cow's eyes");
			helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - yaw)) < 1.0E-3F && Math.abs(stand.getXRot() - pitch) < 1.0E-3F
					&& Math.abs(Mth.wrapDegrees(stand.yHeadRot - yaw)) < 1.0E-3F, "the Stand did not face the cow's eyes (yaw " + yaw
							+ ", pitch " + pitch + "): yaw " + stand.getYRot() + ", pitch " + stand.getXRot() + ", head " + stand.yHeadRot);
			helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.yBodyRot - VIEW_YAW - 30.0F)) < EPS, "1.16 MCUtil.rotateTowards left"
					+ " the body of a Stand with no task it turned at " + (VIEW_YAW + 30.0F) + ": it is at " + stand.yBodyRot);
		});
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.tick ended a barrage clash once the opponent was out of the Stand's reach (isTargetInReach), measured
	 * after updatePosition had put the Stand where it stands that tick: a Stand its user's offset brings back into reach
	 * keeps the clash, one its user takes out of reach ends it on that tick.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theBarrageClashReachIsMeasuredWhereTheStandStandsThatTickAsIn116(GameTestHelper helper) {
		for (boolean backInReach : new boolean[] { true, false }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				Player rival = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
				StandPower rivalPower = null;
				StandEntity stand = scene.stand();
				Player user = scene.user();
				try {
					Vec3 rivalPos = user.position().add(0.0D, 0.0D, 4.0D);
					rival.setNoGravity(true);
					rival.moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
					helper.assertTrue(helper.getLevel().addFreshEntity(rival), "Could not add the rival Stand user");
					rivalPower = PowerClass.STAND.attachGet(rival);
					helper.assertTrue(StandPowerTransitions.insert(rivalPower, new StandInstance(type)).status()
							== StandPowerTransitions.Status.APPLIED && type.summon(rival, rivalPower),
							"Could not give the rival Star Platinum");
					StandEntity opponent = rivalPower.getSummonedStandEntity();
					Vec3 opponentPos = user.position().add(0.0D, 0.0D, 2.0D);
					opponent.setNoGravity(true);
					opponent.moveTo(opponentPos.x, opponentPos.y, opponentPos.z, 180.0F, 0.0F);
					stand.summonLockTicks = 0;
					stand.updatePosition(user);
					double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
					helper.assertTrue(stand.isFollowingUser() && ActionTargetRange.getDistance(stand, opponent.getBoundingBox()) <= reach,
							"setup: the star_platinum does not follow its user within reach of the opposing Stand");
					startBarrageClash(helper, stand, opponent);
					if (backInReach) {
						stand.moveTo(stand.getX(), stand.getY(), stand.getZ() - 12.0D, stand.getYRot(), stand.getXRot());
					}
					else {
						user.moveTo(user.getX(), user.getY(), user.getZ() - 12.0D, user.getYRot(), user.getXRot());
					}
					double before = ActionTargetRange.getDistance(stand, opponent.getBoundingBox());
					stand.setOldPosAndRot();
					stand.tick();
					double after = ActionTargetRange.getDistance(stand, opponent.getBoundingBox());
					String what = backInReach ? "a star_platinum " + before + " blocks from its clash opponent that its user's offset"
							+ " put back " + after + " blocks from it" : "a star_platinum " + before + " blocks from its clash opponent"
									+ " that its user took " + after + " blocks from it";
					helper.assertTrue((before > reach) == backInReach && (after > reach) != backInReach,
							"setup: " + what + " (reach " + reach + ")");
					helper.assertTrue(stand.barrageClashOpponent().isPresent() == backInReach, "1.16 measured the reach of " + what
							+ " (reach " + reach + ") after updatePosition, so it " + (backInReach ? "kept" : "ended") + " the clash"
							+ " on that tick; the clash opponent is " + stand.barrageClashOpponent().orElse(null));
				}
				finally {
					stand.barrageClashStopped();
					if (rivalPower != null && rivalPower.isSummoned()) {
						type.forceUnsummon(rival, rivalPower);
					}
					rival.discard();
				}
			});
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntity.setBarrageClashOpponent(null) also ended the clash of the Stand's previous opponent whenever that
	 * one had any clash opponent (barrageClashOpponent().isPresent()), not only one pointing back at the Stand, and that
	 * one's previous opponent in turn: a clash that ends (here by reach, StandEntity.tick) ends its opponent's clash with a
	 * third Stand and the third Stand's with it, and every client gets the ends. A third Stand pointing at an opponent that
	 * points back at the Stand is no previous opponent of anyone and keeps its clash; an opponent whose own side its barrage
	 * pressed again dropped (BarrageHandler.reset) has no clash opponent, so it is not told and keeps its synced id.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aBarrageClashEndAlsoEndsTheOpponentsClashWithAThirdStandAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "with a third Stand", "pointing back", "dropped by its barrage pressed again" }) {
			StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
				StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
				Player[] rivals = new Player[2];
				StandPower[] rivalPowers = new StandPower[2];
				StandEntity stand = scene.stand();
				StandEntity[] others = new StandEntity[2];
				try {
					for (int i = 0; i < 2; i++) {
						rivals[i] = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
						Vec3 rivalPos = scene.user().position().add(0.0D, 0.0D, 8.0D + 2.0D * i);
						rivals[i].setNoGravity(true);
						rivals[i].moveTo(rivalPos.x, rivalPos.y, rivalPos.z, 180.0F, 0.0F);
						helper.assertTrue(helper.getLevel().addFreshEntity(rivals[i]), "Could not add a rival Stand user");
						rivalPowers[i] = PowerClass.STAND.attachGet(rivals[i]);
						helper.assertTrue(StandPowerTransitions.insert(rivalPowers[i], new StandInstance(type)).status()
								== StandPowerTransitions.Status.APPLIED && type.summon(rivals[i], rivalPowers[i]),
								"Could not give a rival Star Platinum");
						others[i] = rivalPowers[i].getSummonedStandEntity();
						others[i].setNoGravity(true);
					}
					StandEntity opponent = others[0];
					StandEntity third = others[1];
					stand.setCanFollowUser(false);
					stand.setNoGravity(true);
					Vec3 opponentAt = stand.position().add(0.0D, 0.0D, 3.5D);
					opponent.moveTo(opponentAt.x, opponentAt.y, opponentAt.z, 180.0F, 0.0F);
					Vec3 thirdAt = opponentAt.add(0.0D, 0.0D, 2.0D);
					third.moveTo(thirdAt.x, thirdAt.y, thirdAt.z, 0.0F, 0.0F);
					boolean withThird = row.equals("with a third Stand");
					boolean dropped = row.startsWith("dropped");
					// the parries, in order: the Stand's of the opponent, the opponent's of the third Stand (or of the Stand), the
					// third Stand's of the opponent
					setBarrageClashOpponent(stand, opponent);
					setBarrageClashOpponent(opponent, withThird ? third : stand);
					if (!dropped) {
						setBarrageClashOpponent(third, opponent);
					}
					helper.assertTrue(stand.barrageClashOpponent().orElse(null) == opponent
							&& opponent.barrageClashOpponent().orElse(null) == (withThird ? third : stand)
							&& third.barrageClashOpponent().orElse(null) == (dropped ? null : opponent), "setup: the clash opponents are "
									+ stand.barrageClashOpponent() + ", " + opponent.barrageClashOpponent() + " and "
									+ third.barrageClashOpponent());
					if (dropped) {
						// the opponent's user presses its barrage twice: the second press drops its side (StandEntity.onActionSet)
						EntityActionType barrage = (EntityActionType) rivalPowers[0].getAbility("barrage");
						for (int press = 0; press < 2; press++) {
							LivingComponentAction.getComponent(opponent).setAction(
									barrage.initActionOnAbilityUse(helper.getLevel(), rivals[0], opponent, null), rivals[0], SyncType.NO_SYNC);
						}
						helper.assertTrue(opponent.barrageClashOpponent().isEmpty() && syncedClashOpponentId(opponent) == stand.getId(),
								"setup: the opponent's barrage pressed again left it " + opponent.barrageClashOpponent()
										+ " with the synced id " + syncedClashOpponentId(opponent));
					}
					double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
					double fromEyes = ActionTargetRange.getDistance(stand, opponent.getBoundingBox());
					helper.assertTrue(fromEyes > reach, "setup: the opponent is " + fromEyes + " from the Stand's eyes, in its reach "
							+ reach);
					stand.tick();
					String what = "a star_platinum's barrage clash with an opponent that left its reach, the opponent " + switch (row) {
						case "with a third Stand" -> "in a clash with a third Stand that points back at it";
						case "pointing back" -> "pointing back at it, a third Stand pointing at the opponent";
						default -> "pointing back at it until its barrage pressed again dropped its side";
					};
					helper.assertTrue(stand.barrageClashOpponent().isEmpty() && opponent.barrageClashOpponent().isEmpty(),
							"1.16 ended " + what + " for the Stand and the opponent; the clash opponents are "
									+ stand.barrageClashOpponent() + " and " + opponent.barrageClashOpponent());
					int[] synced = { syncedClashOpponentId(stand), syncedClashOpponentId(opponent), syncedClashOpponentId(third) };
					switch (row) {
						case "with a third Stand" -> {
							helper.assertTrue(third.barrageClashOpponent().isEmpty(), "1.16 ended " + what + " for the third Stand"
									+ " too, the opponent's previous opponent; its clash opponent is " + third.barrageClashOpponent());
							helper.assertTrue(synced[0] == -1 && synced[1] == -1 && synced[2] == -1, "1.16 synced the end of " + what
									+ " for all three Stands; the synced ids are " + synced[0] + ", " + synced[1] + " and " + synced[2]);
						}
						case "pointing back" -> {
							helper.assertTrue(third.barrageClashOpponent().orElse(null) == opponent, "1.16 left " + what + " its clash"
									+ " with the opponent; its clash opponent is " + third.barrageClashOpponent());
							helper.assertTrue(synced[0] == -1 && synced[1] == -1 && synced[2] == opponent.getId(), "1.16 synced the end"
									+ " of " + what + " for the Stand and the opponent only; the synced ids are " + synced[0] + ", "
									+ synced[1] + " and " + synced[2]);
						}
						default -> helper.assertTrue(synced[0] == -1 && synced[1] == stand.getId(), "1.16 did not tell the opponent"
								+ " without a clash opponent of " + what + ", so its clients kept showing the clash; the synced ids are "
								+ synced[0] + " and " + synced[1] + ", not -1 and " + stand.getId());
					}
				}
				finally {
					stand.barrageClashStopped();
					if (others[0] != null) {
						LivingComponentAction.getComponent(others[0]).setAction(null, rivals[0], SyncType.NO_SYNC);
					}
					for (int i = 0; i < 2; i++) {
						if (rivalPowers[i] != null && rivalPowers[i].isSummoned()) {
							type.forceUnsummon(rivals[i], rivalPowers[i]);
						}
						if (rivals[i] != null) {
							rivals[i].discard();
						}
					}
				}
			});
		}
		helper.succeed();
	}

	// as when each Stand has parried the other's barrage (StandEntity.barrageClashParryPunches)
	private static void startBarrageClash(GameTestHelper helper, StandEntity stand, StandEntity opponent) {
		setBarrageClashOpponent(stand, opponent);
		setBarrageClashOpponent(opponent, stand);
		helper.assertTrue(stand.barrageClashOpponent().orElse(null) == opponent
				&& opponent.barrageClashOpponent().orElse(null) == stand, "setup: the two Stands are not in a barrage clash");
	}

	// what a Stand's parry of the opponent's barrage does (StandEntity.barrageClashParryPunches)
	private static void setBarrageClashOpponent(StandEntity stand, Entity opponent) {
		try {
			Method clash = StandEntity.class.getDeclaredMethod("setBarrageClashOpponent", Entity.class);
			clash.setAccessible(true);
			clash.invoke(stand, opponent);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: could not set the barrage clash opponent", e);
		}
	}

	// the clash opponent id the server syncs to clients, which set their copy's opponent from it
	@SuppressWarnings("unchecked")
	private static int syncedClashOpponentId(StandEntity stand) {
		try {
			Field key = StandEntity.class.getDeclaredField("BARRAGE_CLASH_OPPONENT_ID");
			key.setAccessible(true);
			return stand.getEntityData().get((EntityDataAccessor<Integer>) key.get(null));
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("could not read the synced barrage clash opponent", e);
		}
	}

	// an action that gives the Stand its user's yaw after the task's turn, as 1.16 StunComboAttack.moveStandForward did
	private static final class YawToUserProbe extends StandEntityAbility {
		YawToUserProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, EntityActionInstance::new);
		}

		@Override
		protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
			return target.getType() == ActionTarget.TargetType.ENTITY;
		}

		@Override
		public void rotateStandTowardsTarget(StandEntity standEntity, EntityActionInstance action, ActionTarget target) {
			super.rotateStandTowardsTarget(standEntity, action, target);
			float yaw = standEntity.getUser().getYRot();
			standEntity.setYRot(yaw);
			standEntity.setYBodyRot(yaw);
		}
	}

	// an action whose tick turns its Stand off its user's look, as a 1.16 action's setRot in its task's tick did
	private static final class YawAsideProbe extends StandEntityAbility {
		YawAsideProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, YawAsideAction::new);
			setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		}

		@Override
		protected boolean keepsTaskTargetType(StandEntity standEntity, ActionTarget target) {
			return target.getType() == ActionTarget.TargetType.ENTITY;
		}
	}

	private static final class YawAsideAction extends EntityActionInstance {
		static final float ASIDE = 40.0F;

		YawAsideAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void actionTick() {
			if (getPerformer() instanceof StandEntity stand && stand.getUser() != null) {
				stand.setYRot(stand.getUser().getYRot() + ASIDE);
			}
		}
	}

	// an action that moves its Stand by itself (1.16 standMovesByItself, noAdheringToUserOffset)
	private static final class SelfMovingProbe extends StandEntityAbility {
		SelfMovingProbe(AbilityType<?> abilityType, AbilityId abilityId) {
			super(abilityType, abilityId, EntityActionInstance::new);
			setDefaultPhaseLength(ActionPhase.PERFORM, 40);
		}

		@Override
		public boolean noAdheringToUserOffset(StandPower standPower, StandEntity standEntity) {
			return true;
		}

		@Override
		public boolean noAdheringToUserOffsetClientFallback(StandEntity standEntity) {
			return true;
		}
	}

	private static final class ClientProbeAction extends EntityActionInstance {
		ClientProbeAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}

	// StandEntity.setTaskTarget called by other server code, with a client tracking the Stand
	private static List<TrEntityActionTargetPacket> setTaskTargetWatched(GameTestHelper helper, Scene scene, EntityActionInstance action,
			@Nullable ActionTarget target) {
		StandAttackAimRulesGameTests.TargetWatcher watcher = new StandAttackAimRulesGameTests.TargetWatcher(helper.getLevel());
		watcher.track(helper, scene.stand());
		try {
			scene.stand().setTaskTarget(target);
		}
		finally {
			watcher.untrack();
		}
		return watcher.sentFor(action);
	}

	// a client's copy of the action (EntityActionInstance.takesTargetsFromServer)
	private static final class ClientStringAttack extends StringAttackShot {
		ClientStringAttack(EntityActionType ability) {
			super(ability, false);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}

	private static final class ClientHeal extends HealingAction {
		ClientHeal(EntityActionType ability) {
			super(ability);
		}

		@Override
		protected boolean takesTargetsFromServer() {
			return true;
		}
	}

	private static boolean isNone(@Nullable ActionTarget target) {
		return target == null || target.getType() == ActionTarget.TargetType.EMPTY;
	}

	private static void assertLooksAlongItsUser(GameTestHelper helper, Scene scene, String claim) {
		float yaw = scene.stand().getYRot();
		float pitch = scene.stand().getXRot();
		helper.assertTrue(Math.abs(Mth.wrapDegrees(yaw - scene.user().getYRot())) < EPS
				&& Math.abs(pitch - scene.user().getXRot()) < EPS, claim + ": it looks at yaw " + yaw + ", pitch " + pitch
						+ " instead of the user's " + scene.user().getYRot() + ", " + scene.user().getXRot());
	}

	// the strings a String Attack shot as its perform started
	private static void discardStrings(Scene scene) {
		scene.spawned().addAll(scene.helper().getLevel().getEntitiesOfClass(HGStringEntity.class,
				scene.user().getBoundingBox().inflate(16.0D)));
	}
}

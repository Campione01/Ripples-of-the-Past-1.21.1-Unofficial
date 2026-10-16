package rotp.core.gametest;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility.HealingAction;
import rotp.core.impl.stands.hierophant.HGStringEntity;
import rotp.core.impl.stands.hierophant.HierophantStringAttackAbility.StringAttackShot;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.netcode.TrEntityActionTargetPacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.PrimedTnt;
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

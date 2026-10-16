package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands.hierophant.HGStringEntity;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
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

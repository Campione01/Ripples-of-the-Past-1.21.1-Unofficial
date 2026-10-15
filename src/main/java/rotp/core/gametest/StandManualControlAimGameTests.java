package rotp.core.gametest;

import java.util.function.Predicate;
import java.util.function.Supplier;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.CrosshairRule;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Attacks of a manually controlled Stand take their target from the Stand's own aim and eyes, never from its user's
 * crosshair (1.16 StandEntity.aimWithThisOrUser skipped the user under manual control and cast the precision ray from
 * the Stand, precisionRayTrace(this, getAimDistance)). Scenes are those of {@link StandAttackAimRulesGameTests}. Each
 * test has its own batch: the rays reach past the test area.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualControlAimGameTests {
	private static final String BATCH = "stand_manual_control_aim_";

	private StandManualControlAimGameTests() {}

	private interface Step {
		boolean run(int tick);
	}

	// Scene.run takes a private step type of its own class
	private static void run(StandAttackAimRulesGameTests.Scene scene, Step step, int lastTick, Supplier<String> timeoutMessage) {
		scene.helper.onEachTick(() -> {
			if (scene.finished) return;
			try {
				if (step.run(++scene.ticks)) {
					scene.finished = true;
					scene.close();
					scene.helper.succeed();
				}
				else if (scene.ticks >= lastTick) {
					scene.finished = true;
					scene.close();
					scene.helper.fail(timeoutMessage.get());
				}
			}
			catch (RuntimeException | Error error) {
				scene.finished = true;
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
}

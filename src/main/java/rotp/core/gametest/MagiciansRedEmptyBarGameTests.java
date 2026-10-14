package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.magiciansred.MRFireballEntity;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedFireballAbility.FireballShot;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility.FlameBurstShot;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 MagiciansRedFireball (staminaCost 75) and MagiciansRedFlameBurst (holdType, staminaCostTick 3) had no
 * stamina condition: the press started on any bar, the Stand task shot and then paid, ignoring a failed payment,
 * and a short bar dropped to 0 (StandEntityTask, StandPower.consumeStamina).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagiciansRedEmptyBarGameTests {
	private MagiciansRedEmptyBarGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void fireballFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
		EmptyStaminaBarScene scene = new EmptyStaminaBarScene(helper);
		List<Float> staminaAfterShot = new ArrayList<>();
		try {
			scene.start("magicians_red", -90, 2, "fireball");
			List<Entity> fireballs = scene.trackSpawns(entity -> entity instanceof MRFireballEntity
					&& entity.distanceTo(scene.stand) < 4);
			scene.afterStandTick(() -> {
				if (!fireballs.isEmpty() && staminaAfterShot.isEmpty()) {
					staminaAfterShot.add(scene.power.getStamina());
				}
			});
			helper.assertTrue(scene.pressOnBar("fireball", InputMethod.CLICK, 30),
					"1.16 had no stamina condition: the Fireball must be admitted on a 30 bar");
			helper.assertTrue(scene.stand.getCurStandAction() instanceof FireballShot,
					"The Fireball press did not start the shot");
			helper.runAfterDelay(15, () -> {
				try {
					helper.assertTrue(fireballs.size() == 1, "1.16 shot one fireball whatever the bar, got " + fireballs.size());
					helper.assertTrue(staminaAfterShot.size() == 1 && staminaAfterShot.get(0) == 0,
							"The 75 cost paid on a 30 bar drops it to 0: " + staminaAfterShot);
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			scene.close();
			throw failure;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void flameBurstKeepsBurningOnAnEmptyBar(GameTestHelper helper) {
		EmptyStaminaBarScene scene = new EmptyStaminaBarScene(helper);
		float[] maxStaminaWhileBurning = {-1};
		try {
			scene.start("magicians_red", -90, 0);
			List<Entity> flames = scene.trackSpawns(entity -> entity instanceof MRFlameEntity
					&& entity.distanceTo(scene.stand) < 4);
			scene.afterStandTick(() -> {
				if (scene.stand.getCurStandAction() instanceof FlameBurstShot burst && burst.getPhase() == ActionPhase.PERFORM) {
					maxStaminaWhileBurning[0] = Math.max(maxStaminaWhileBurning[0], scene.power.getStamina());
				}
			});
			helper.assertTrue(scene.pressOnBar("flame_burst", InputMethod.HOLD, 0),
					"1.16 had no stamina condition: Flame Burst must be admitted on an empty bar");
			helper.runAfterDelay(25, () -> {
				try {
					EntityActionInstance action = scene.stand.getCurStandAction();
					helper.assertTrue(action instanceof FlameBurstShot && action.getPhase() == ActionPhase.PERFORM,
							"A held Flame Burst must keep burning on an empty bar, but its action is " + action
									+ (action != null ? " in " + action.getPhase() : ""));
					helper.assertTrue(flames.size() >= 15, "Flame Burst shot a flame each tick in 1.16, got " + flames.size());
					helper.assertTrue(maxStaminaWhileBurning[0] == 0,
							"The 3 per tick paid on an empty bar keeps it at 0: highest " + maxStaminaWhileBurning[0]);
				}
				finally {
					scene.close();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error failure) {
			scene.close();
			throw failure;
		}
	}
}

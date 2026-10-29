package rotp.addons.diverdown.ability;

import java.lang.reflect.Field;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;

/**
 * 1.16 DiverDownDeformDash.replaceAction (049 :79-81): below half a finisher meter the heavy key was Diver Down's heavy
 * punch again. StandEntityHeavyAttack.getFinisherVariationIfPresent asked the running dash, whose press had spent 0.51
 * of the meter (onTaskSet), so through the whole dash the key was the heavy punch: pressed in the dash's perform it was
 * queued (canBeQueued) and followed the dash, pressed in its recovery it started at once (a finisher's free recovery,
 * StandEntityAction.canBeCanceled :507-512); it never started a second dash.
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeformDashHeavyKeyGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;
    private static final short KEY = 21;

    private DeformDashHeavyKeyGameTests() {}

    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_dash_heavy_key",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void theHeavyKeyDuringTheDeformDashIsTheHeavyPunchAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            for (ActionPhase pressedIn : new ActionPhase[] {ActionPhase.PERFORM, ActionPhase.RECOVERY}) {
                DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
                try {
                    pressDuringTheDash(helper, scene, pressedIn);
                } finally {
                    scene.close();
                }
            }
            helper.succeed();
        });
    }

    private static void pressDuringTheDash(GameTestHelper helper, DiverDownWallsGameTests.Scene scene,
            ActionPhase pressedIn) {
        DiverDownStandEntity stand = scene.stand;
        LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
        EntityActionInputState input = scene.owner.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
        Ability heavy = scene.power.getAbility(DiverDownAddon.HEAVY_PUNCH);
        helper.assertTrue(heavy instanceof DiverDownHeavyPunchAbility, "Fixture: Diver Down's heavy punch is missing");
        // the dash's strikes aim again through the user and the Stand: both look at the sky, so nothing is struck
        scene.turn(0.0F, -90.0F);
        stand.setXRot(-90.0F);
        stand.xRotO = -90.0F;

        stand.setFinisherMeter(1.0F);
        AbilityConditionCheck key = resolve(scene);
        helper.assertTrue(key.ability instanceof DeformDashAbility && key.conditionCheck.isPositive(),
                "Fixture: a full finisher meter does not give the heavy key to the deform dash: " + describe(key));
        helper.assertTrue(press(scene, key, heavy), "Fixture: the heavy key at a full meter was refused");
        EntityActionInstance dash = standAction.getAction();
        helper.assertTrue(dash instanceof DeformDashAbility.DeformDashAction,
                "Fixture: the heavy key at a full meter did not start the deform dash: " + dash);
        for (int tick = 0; tick < OWN_TICKS && dash.getPhase() != pressedIn && !dash.isOver(); tick++) {
            standAction.tick();
        }
        helper.assertTrue(dash.getPhase() == pressedIn && standAction.getAction() == dash,
                "Fixture: the deform dash did not reach its " + pressedIn + ": phase " + dash.getPhase());
        float meter = stand.getFinisherMeter();
        helper.assertTrue(meter < 0.5F, "Fixture: the deform dash did not spend its finisher meter: " + meter);
        float stamina = scene.power.getStamina();
        helper.assertTrue(scene.power.getMaxStamina() - stamina > 199.0F,
                "Fixture: the deform dash did not pay its 200 stamina as its perform began: " + stamina + " of "
                        + scene.power.getMaxStamina());

        key = resolve(scene);
        helper.assertTrue(key.ability == heavy && key.conditionCheck.isPositive(),
                "1.16 gave the heavy key back to Diver Down's heavy punch while the deform dash ran with its meter "
                        + "spent below half (" + meter + "); in the dash's " + pressedIn + " the key holds "
                        + describe(key));
        helper.assertTrue(press(scene, key, heavy),
                "1.16 accepted the heavy punch pressed in the deform dash's " + pressedIn + "; the port refused it");
        if (pressedIn == ActionPhase.PERFORM) {
            helper.assertTrue(standAction.getAction() == dash && dash.getPhase() == ActionPhase.PERFORM,
                    "1.16 queued the heavy punch pressed in the deform dash's perform and let the dash go on; the "
                            + "Stand's action is " + standAction.getAction() + ", the dash in " + dash.getPhase());
            ActionPhase dashPhase = dash.getPhase();
            for (int tick = 0; tick < OWN_TICKS && standAction.getAction() == dash && !dash.isOver(); tick++) {
                standAction.tick();
                dashPhase = dash.getPhase();
                resolve(scene);
                input.tick();
            }
            helper.assertTrue(dashPhase == ActionPhase.RECOVERY,
                    "1.16 started the queued heavy punch once the deform dash's free recovery could take it; it "
                            + "started in the dash's " + dashPhase);
        }
        EntityActionInstance punch = standAction.getAction();
        helper.assertTrue(punch instanceof StandEntityHeavyPunchAbility.StandEntityHeavyPunch && punch.ability == heavy,
                "1.16 started Diver Down's heavy punch from the heavy key pressed in the deform dash's " + pressedIn
                        + " (not a second dash); the Stand's action is "
                        + (punch != null ? punch.ability + " " + punch.getPhase() : null));
        helper.assertTrue(dash.isOver() || standAction.getAction() != dash,
                "The deform dash still runs beside the heavy punch");
        for (int tick = 0; tick < OWN_TICKS && standAction.getAction() == punch
                && punch.getPhase() != ActionPhase.RECOVERY && !punch.isOver(); tick++) {
            standAction.tick();
        }
        helper.assertTrue(punch.getPhase() == ActionPhase.RECOVERY || punch.isOver(),
                "Fixture: the heavy punch did not strike: phase " + punch.getPhase());
        float spent = stamina - scene.power.getStamina();
        helper.assertTrue(Math.abs(spent - 50.0F) < 0.5F,
                "1.16 charged the heavy punch's own 50 stamina for the press in the deform dash's " + pressedIn
                        + ", not the dash's 200 again; it took " + spent);
    }

    private static boolean press(DiverDownWallsGameTests.Scene scene, AbilityConditionCheck key, Ability base) {
        // the server's gate of a key press (ClAbilityInputPacket), then the press with the base key for the buffer
        boolean admitted = AbilityInput.withConditionCheck(key, scene.owner, InputMethod.CLICK);
        if (admitted) {
            AbilityInput.keyPress(KEY, key.ability, scene.owner, null, InputMethod.CLICK, 0,
                    BufferingState.clickCanBuffer(), base.getAbilityId());
            AbilityInput.keyRelease(KEY, scene.owner);
        }
        return admitted;
    }

    // the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
    private static AbilityConditionCheck resolve(DiverDownWallsGameTests.Scene scene) {
        try {
            Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
            cached.setAccessible(true);
            cached.setBoolean(scene.power, false);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
        }
        AbilityConditionCheck key = scene.power.updateAvailableMoves()
                .getContextVariationContainer(DiverDownAddon.HEAVY_PUNCH);
        scene.helper.assertTrue(key != null, "The heavy key holds nothing");
        return key;
    }

    private static String describe(AbilityConditionCheck key) {
        return key.ability.name() + " (" + (key.conditionCheck.isPositive() ? "usable" : "refused") + ")";
    }
}

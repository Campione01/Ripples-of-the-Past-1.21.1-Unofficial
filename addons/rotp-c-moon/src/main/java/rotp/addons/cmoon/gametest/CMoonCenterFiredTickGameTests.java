package rotp.addons.cmoon.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.CenterOfGravityAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;

/**
 * 1.16 CenterOfGravity (holdToFire(10), no recovery) fired in the charge's 10th tick and its Stand's task ran the
 * perform's first tick in the Stand's next tick; once the field ran it was isCancelable to another action. The port
 * enters the perform at the end of the Stand's 10th tick: a punch pressed before the field's first tick waits (the
 * core's rule for a fired Stand charge, EntityActionInstance.isFiredStandCharge; 1.16 isCancelable let it cancel the
 * task there), one pressed after it replaces the field at once.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonCenterFiredTickGameTests {
    private static final short FIRST_PUNCH_KEY = 213;
    private static final short SECOND_PUNCH_KEY = 214;

    private CMoonCenterFiredTickGameTests() {}

    @GameTest(template = "empty", batch = "cmCenterFiredTick", timeoutTicks = 100)
    public static void aPunchWaitsForTheFieldsFirstTickThenReplacesIt(GameTestHelper helper) {
        GravityTestSupport.Actor actor = new GravityTestSupport.Actor(helper, false, false, false);
        helper.runAfterDelay(3, () -> {
            try {
                run(helper, actor);
            }
            finally {
                try { AbilityInput.keyRelease(FIRST_PUNCH_KEY, actor.user); }
                finally {
                    try { AbilityInput.keyRelease(SECOND_PUNCH_KEY, actor.user); }
                    finally { actor.close(); }
                }
            }
            helper.succeed();
        });
    }

    private static void run(GameTestHelper helper, GravityTestSupport.Actor actor) {
        Ability centerAbility = actor.power.getAbility(CMoonAddon.CENTER_OF_GRAVITY);
        var checked = actor.resolved(centerAbility);
        actor.premise(checked.conditionCheck.isPositive()
                && AbilityInput.withConditionCheck(checked, actor.user, InputMethod.HOLD), "Center input admitted");
        BufferingState held = BufferingState.clickOnly();
        AbilityInput.keyPress(GravityTestSupport.KEY, checked.ability, actor.user, null, InputMethod.HOLD,
                0, held, centerAbility.getAbilityId());
        EntityActionInstance center = actor.stand.getCurStandAction();
        actor.premise(held.isActionSuccess && center instanceof CenterOfGravityAbility.CenterAction
                && center.getPhase() == ActionPhase.BUTTON_CHARGE, "real Center charge started");

        // the charge has fired: the perform's first tick has not run yet
        center.setPhase(ActionPhase.PERFORM, 0);
        BufferingState early = pressPunch(actor, FIRST_PUNCH_KEY);
        helper.assertTrue(!early.isActionSuccess && early.shouldBuffer && actor.stand.getCurStandAction() == center
                        && !center.isOver() && center.getPhase() == ActionPhase.PERFORM,
                "the core keeps a fired Stand charge's perform until its first tick: a punch pressed before it must"
                        + " wait (started " + early.isActionSuccess + ", buffered " + early.shouldBuffer
                        + ", Stand action " + actor.stand.getCurStandAction() + ")");

        center.setPhase(ActionPhase.PERFORM, 1);
        BufferingState late = pressPunch(actor, SECOND_PUNCH_KEY);
        EntityActionInstance now = actor.stand.getCurStandAction();
        helper.assertTrue(late.isActionSuccess && !late.shouldBuffer && center.isOver() && now != null
                        && now != center && now.ability == actor.power.getAbility(CMoonAddon.PUNCH),
                "1.16 isCancelable: once its field ran, Center gives way to a punch at once (started "
                        + late.isActionSuccess + ", buffered " + late.shouldBuffer + ", Stand action " + now + ")");
    }

    private static BufferingState pressPunch(GravityTestSupport.Actor actor, short key) {
        Ability punch = actor.power.getAbility(CMoonAddon.PUNCH);
        var checked = actor.resolved(punch);
        actor.premise(AbilityInput.withConditionCheck(checked, actor.user, InputMethod.CLICK), "punch input admitted");
        BufferingState buffering = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(key, checked.ability, actor.user, null, InputMethod.CLICK, 0, buffering,
                punch.getAbilityId());
        return buffering;
    }
}

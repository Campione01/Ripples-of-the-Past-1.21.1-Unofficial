package rotp.addons.d4c;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

/**
 * D4C's rapid punch (aa/aa/ac.java, a StandEntityLightAttack) and hand chop (aa/aa/aa.java, a StandEntityHeavyAttack)
 * against what their 1.16 base classes gave them.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMeleeParityGameTests {
    private D4CMeleeParityGameTests() {}

    /**
     * 1.16 StandEntityLightAttack.isCancelable (StandEntityLightAttack.java:127-129): the rapid punch's recovery gave way
     * to any other action at once; not to the rapid punch itself, which ac.java:173-175 chained only while fewer than 6
     * hits had landed (StandEntityAction.canClickDuringTask), and all 6 land in the perform. Its perform held presses
     * back.
     */
    @GameTest(template = "empty", batch = "d4cMeleeRapidRecovery", timeoutTicks = 100)
    public static void rapidPunchRecoveryGivesWayToOtherActionsButNotItself(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-rapid-recovery")) {
            aimAtNothing(scene);
            EntityActionType rapidType = scene.actionType("d4c_rapid_punch");
            EntityActionType chopType = scene.actionType("d4c_hand_chop");
            EntityActionInstance rapid = scene.start("d4c_rapid_punch");
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            for (int tick = 0; tick < 10 && rapid.getPhase() != ActionPhase.PERFORM; tick++) {
                component.tick();
            }
            helper.assertTrue(rapid.getPhase() == ActionPhase.PERFORM && !rapid.canBeCancelledInto(chopType),
                    "1.16 held presses back during the rapid punch's perform (" + rapid.getPhase() + ")");
            for (int tick = 0; tick < 40 && rapid.getPhase() != ActionPhase.RECOVERY; tick++) {
                component.tick();
            }
            helper.assertTrue(rapid.getPhase() == ActionPhase.RECOVERY,
                    "fixture: the rapid punch did not reach its recovery: " + rapid.getPhase());
            helper.assertTrue(rapid.canBeCancelledInto(chopType) && !chopType.shouldBufferInput(component),
                    "1.16 StandEntityLightAttack.isCancelable let another action replace the rapid punch in its "
                            + "recovery; the port holds the hand chop's press back");
            helper.assertTrue(!rapid.canBeCancelledInto(rapidType),
                    "1.16 did not chain the rapid punch into itself after its 6 hits (ac.isChainable); the port does");
            helper.assertTrue(scene.click("d4c_hand_chop", (short) 301),
                    "1.16 replaced the rapid punch in its recovery with the hand chop pressed then; the press did not "
                            + "start it: " + scene.stand.getCurStandAction() + " " + scene.keys());
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityHeavyAttack.isFreeRecovery (:228) let any other action replace the hand chop, a finisher, in its
     * recovery; the hand chop pressed again was refused (StandEntityAction.canClickDuringTask :495-497, a heavy attack is
     * not chainable), so the chop did not restart in its own recovery.
     */
    @GameTest(template = "empty", batch = "d4cMeleeChopNoRestart", timeoutTicks = 100)
    public static void handChopRecoveryGivesWayToOtherActionsButNotItself(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-chop-no-restart")) {
            aimAtNothing(scene);
            EntityActionType chopType = scene.actionType("d4c_hand_chop");
            EntityActionType rapidType = scene.actionType("d4c_rapid_punch");
            scene.stand.setFinisherMeter(0.6F);
            EntityActionInstance chop = scene.start("d4c_hand_chop");
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            for (int tick = 0; tick < 40 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                component.tick();
            }
            helper.assertTrue(chop.getPhase() == ActionPhase.RECOVERY && !chop.isOver(),
                    "fixture: the hand chop did not reach its recovery: " + chop.getPhase());
            // a full meter again, so the heavy key would be the chop once more
            scene.stand.setFinisherMeter(0.6F);
            helper.assertTrue(!chop.canBeCancelledInto(chopType) && chopType.shouldBufferInput(component),
                    "1.16 refused the hand chop pressed in its own recovery (canClickDuringTask, not chainable); "
                            + "the port lets it replace itself");
            boolean restarted = scene.click("d4c_hand_chop", (short) 305);
            helper.assertTrue(!restarted && scene.stand.getCurStandAction() == chop
                            && chop.getPhase() == ActionPhase.RECOVERY,
                    "1.16 did not restart the hand chop pressed in its own recovery; the port's press "
                            + (restarted ? "restarted it" : "left " + scene.stand.getCurStandAction()));
            helper.assertTrue(chop.canBeCancelledInto(rapidType) && scene.click("d4c_rapid_punch", (short) 306),
                    "1.16 StandEntityHeavyAttack.isFreeRecovery let another action replace the hand chop in its "
                            + "recovery; the rapid punch did not start: " + scene.stand.getCurStandAction() + " "
                            + scene.keys());
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.onTaskSet (StandEntityLightAttack.java:132-140) alternated the Stand's hands when the
     * rapid punch's task was set. (Its punchComboCount picked among the 'rapidpunch' clips; D4C has one.)
     */
    @GameTest(template = "empty", batch = "d4cMeleeRapidHands", timeoutTicks = 100)
    public static void rapidPunchAlternatesTheStandsHands(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-rapid-hands")) {
            aimAtNothing(scene);
            // a press more than a tick after the last swing: StandEntity.alternateHands takes the other hand
            scene.stand.tickCount += 10;
            InteractionHand hand = scene.stand.getPunchingHand();
            scene.start("d4c_rapid_punch");
            helper.assertTrue(scene.stand.getCurrentPunchingHand() == hand && scene.stand.getPunchingHand() != hand,
                    "1.16 StandEntityLightAttack.onTaskSet alternated D4C's hands: the rapid punch swings " + hand
                            + "; the port swings " + scene.stand.getCurrentPunchingHand() + ", next "
                            + scene.stand.getPunchingHand());
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityTask.moveToPhase (StandEntityTask.java:181) read the hand chop's getStandRecoveryTicks as its
     * recovery began: round(getHeavyAttackRecovery(speed, lastHeavyFinisherValue) x 0.8) (aa.java:69-72), with the
     * finisher value recorded last; a heavy press during the chop recorded it again
     * (StandEntityHeavyAttack.onClick:130-134).
     */
    @GameTest(template = "empty", batch = "d4cMeleeChopRecovery", timeoutTicks = 100)
    public static void handChopRecoveryIsReadAsTheRecoveryBegins(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-chop-recovery")) {
            aimAtNothing(scene);
            scene.stand.setFinisherMeter(0.6F);
            EntityActionInstance chop = scene.start("d4c_hand_chop");
            float atPress = chop.phasesLength.getFloat(ActionPhase.RECOVERY);
            // the finisher value a heavy press during the windup records
            scene.stand.setFinisherMeter(0.0F);
            scene.stand.setHeavyPunchFinisher();
            int expected = Math.round(StandStatFormulas.getHeavyAttackRecovery(
                    scene.stand.getAttackSpeed(), scene.stand.getLastHeavyFinisherValue()) * 0.8F);
            helper.assertTrue(expected != atPress,
                    "fixture: the two finisher values give the same recovery " + expected);
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            for (int tick = 0; tick < 40 && chop.getPhase() != ActionPhase.RECOVERY; tick++) {
                component.tick();
            }
            helper.assertTrue(chop.getPhase() == ActionPhase.RECOVERY,
                    "fixture: the hand chop did not reach its recovery: " + chop.getPhase());
            int ticks = 0;
            while (ticks < 40 && scene.stand.getCurStandAction() == chop && !chop.isOver()) {
                component.tick();
                ticks++;
            }
            helper.assertTrue(ticks == expected,
                    "1.16 read the hand chop's recovery as it began, from the finisher value recorded last: "
                            + expected + " ticks; the port, which took " + atPress + " at the press, ran " + ticks);
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.checkStandConditions (:53-55) and StandEntityHeavyAttack.checkStandConditions
     * (:125-127): a Stand that cannot attack in melee (StandEntity.canAttackMelee) refused the rapid punch and the hand
     * chop without a message.
     */
    @GameTest(template = "empty", batch = "d4cMeleeCanAttack", timeoutTicks = 100)
    public static void rapidPunchAndHandChopNeedAStandThatCanAttackInMelee(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-melee-can-attack")) {
            aimAtNothing(scene);
            AttributeInstance speed = scene.stand.getAttribute(Attributes.ATTACK_SPEED);
            helper.assertTrue(speed != null, "fixture: D4C has no attack speed");
            double base = speed.getBaseValue();
            try {
                speed.setBaseValue(0.0D);
                helper.assertTrue(!scene.stand.canAttackMelee(), "fixture: D4C can still attack in melee");
                float stamina = scene.power.getStamina();
                boolean rapid = scene.click("d4c_rapid_punch", (short) 311);
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                boolean chop = scene.click("d4c_hand_chop", (short) 312);
                helper.assertTrue(!rapid && !chop && scene.power.getStamina() == stamina && scene.keys().isEmpty(),
                        "1.16 checkStandConditions refused the rapid punch and the hand chop of a Stand that cannot "
                                + "attack in melee, without a message; started: rapid punch " + rapid + ", hand chop "
                                + chop + ", messages " + scene.keys());
            }
            finally {
                speed.setBaseValue(base);
            }
            helper.assertTrue(scene.stand.canAttackMelee() && scene.click("d4c_rapid_punch", (short) 313),
                    "fixture: the rapid punch does not start with the attack speed back");
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityMeleeBarrage.isCancelable (:179-190): in its recovery the barrage gave way to an action that
     * canFollowUpBarrage, which StandEntityHeavyAttack was (:222-225) and so the hand chop; a light attack such as the
     * rapid punch was not.
     */
    @GameTest(template = "empty", batch = "d4cMeleeBarrageFollowUp", timeoutTicks = 100)
    public static void handChopFollowsUpTheBarrageInItsRecovery(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-barrage-follow-up")) {
            aimAtNothing(scene);
            EntityActionType chopType = scene.actionType("d4c_hand_chop");
            EntityActionType rapidType = scene.actionType("d4c_rapid_punch");
            EntityActionInstance barrage = scene.start("d4c_barrage");
            barrage.setPhaseStart(ActionPhase.RECOVERY);
            LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
            helper.assertTrue(barrage.getPhase() == ActionPhase.RECOVERY && !barrage.isOver(),
                    "fixture: the barrage is not in its recovery: " + barrage.getPhase());
            helper.assertTrue(!barrage.canBeCancelledInto(rapidType),
                    "fixture: the barrage's recovery gives way to a light attack, which 1.16 did not");
            helper.assertTrue(barrage.canBeCancelledInto(chopType) && !chopType.shouldBufferInput(component),
                    "1.16 let a heavy attack (canFollowUpBarrage), the hand chop too, follow the barrage up in its "
                            + "recovery; the port holds the hand chop's press back");
            helper.assertTrue(scene.click("d4c_hand_chop", (short) 321),
                    "1.16 replaced the barrage in its recovery with the hand chop pressed then; the press did not "
                            + "start it: " + scene.stand.getCurStandAction() + " " + scene.keys());
            helper.succeed();
        }
    }

    private static void aimAtNothing(D4CGameTestScene scene) {
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
        LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(ActionTarget.EMPTY);
    }
}

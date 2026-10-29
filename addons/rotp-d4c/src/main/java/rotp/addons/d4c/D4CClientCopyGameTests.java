package rotp.addons.d4c;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.ability.D4CPhase2BAbility;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;

/**
 * A client's copy of a synced D4C action is of its power type's base-moveset instance of the ability
 * (EntityActionType#encodeAbility sends it without its user), not of the user's own one that the client's moveset, keys
 * and HUD hold. 1.16's client asked the same questions of its synced task as the server of its own, so each row builds
 * the action once of the user's own ability, as the server holds it, and once of the base-moveset one, as the client
 * holds it, and asks the user's ability the same question of both.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CClientCopyGameTests {
    private D4CClientCopyGameTests() {}

    /**
     * 1.16 ac.java:116 standMovesByItself: from the last 2 windup ticks on, the rapid punch's Stand stays where its front
     * offset put it and is not moved by hand. The check compares the Stand's running action with the ability that asks.
     */
    @GameTest(template = "empty", batch = "d4cClientCopyMoves", timeoutTicks = 100)
    public static void rapidPunchMovesByItselfOnItsUsersClientAsOnTheServer(GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-copy-moves")) {
            aimAtNothing(scene);
            D4CPhase2BAbility own = rapidPunch(scene);
            for (Copy copy : Copy.values()) {
                EntityActionInstance rapid = copy.start(scene, "d4c_rapid_punch");
                LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
                for (int tick = 0; tick < 10 && rapid.getPhase() != ActionPhase.PERFORM; tick++) {
                    component.tick();
                }
                helper.assertTrue(rapid.getPhase() == ActionPhase.PERFORM,
                        "fixture: the rapid punch of " + copy.side + " did not reach its perform: " + rapid.getPhase());
                helper.assertTrue(own.noAdheringToUserOffset(scene.power, scene.stand)
                                && own.noAdheringToUserOffsetClientFallback(scene.stand)
                                && own.lockStandManualMovement(scene.power, scene.stand),
                        "1.16 kept the Stand off its user's offset and out of hand movement in the rapid punch's perform;"
                                + " asked of the user's ability with the action of " + copy.side + " the answers are "
                                + own.noAdheringToUserOffset(scene.power, scene.stand) + ", "
                                + own.noAdheringToUserOffsetClientFallback(scene.stand) + ", "
                                + own.lockStandManualMovement(scene.power, scene.stand));
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            }
            helper.succeed();
        }
    }

    /**
     * 1.16 StandEntityLightAttack.isCancelable and StandEntityHeavyAttack.isFreeRecovery let any other action replace the
     * rapid punch and the hand chop in their recovery, and StandEntityAction.canClickDuringTask refused neither's own
     * press (not chainable after its 6 hits, a heavy attack never): the client predicts a press as buffered or not from
     * the same answer.
     */
    @GameTest(template = "empty", batch = "d4cClientCopyRecovery", timeoutTicks = 100)
    public static void rapidPunchAndHandChopRecoveryHoldTheirOwnPressOnItsUsersClientAsOnTheServer(
            GameTestHelper helper) {
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-copy-recovery")) {
            aimAtNothing(scene);
            for (String name : new String[] {"d4c_rapid_punch", "d4c_hand_chop"}) {
                EntityActionType own = scene.actionType(name);
                EntityActionType other = scene.actionType(name.equals("d4c_rapid_punch") ? "d4c_hand_chop" : "d4c_rapid_punch");
                for (Copy copy : Copy.values()) {
                    scene.stand.setFinisherMeter(0.6F);
                    EntityActionInstance action = copy.start(scene, name);
                    action.setPhaseStart(ActionPhase.RECOVERY);
                    LivingComponentAction component = LivingComponentAction.getComponent(scene.stand);
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY && !action.isOver(),
                            "fixture: " + name + " of " + copy.side + " is not in its recovery: " + action.getPhase());
                    helper.assertTrue(!action.canBeCancelledInto(own) && own.shouldBufferInput(component),
                            "1.16 refused " + name + " pressed in its own recovery (canClickDuringTask); the press of"
                                    + " the user's ability against the action of " + copy.side + " is let in: "
                                    + action.canBeCancelledInto(own) + ", held back " + own.shouldBufferInput(component));
                    helper.assertTrue(action.canBeCancelledInto(other) && !other.shouldBufferInput(component),
                            "1.16 let another action replace " + name + " in its recovery; the press of the other"
                                    + " ability against the action of " + copy.side + " is held back");
                    LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
                }
            }
            helper.succeed();
        }
    }

    private static D4CPhase2BAbility rapidPunch(D4CGameTestScene scene) {
        scene.helper.assertTrue(scene.actionType("d4c_rapid_punch") instanceof D4CPhase2BAbility,
                "fixture: the rapid punch is not a D4CPhase2BAbility");
        return (D4CPhase2BAbility) scene.actionType("d4c_rapid_punch");
    }

    private static void aimAtNothing(D4CGameTestScene scene) {
        LivingComponentAction.getComponent(scene.owner).entityAim.setTarget(ActionTarget.EMPTY);
        LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(ActionTarget.EMPTY);
    }

    private enum Copy {
        SERVER("the server, of the user's own ability"),
        CLIENT("its user's client, of the base-moveset ability");

        private final String side;

        Copy(String side) {
            this.side = side;
        }

        // the action set on D4C as a press sets it, of the user's own ability or of the power type's base-moveset one
        EntityActionInstance start(D4CGameTestScene scene, String name) {
            Ability own = scene.power.getAbility(name);
            Ability base = scene.power.getPowerType().getBaseMoveset().getAbility(name);
            scene.helper.assertTrue(own instanceof EntityActionType && base instanceof EntityActionType && base != own
                            && base.getAbilityId().equals(own.getAbilityId()),
                    "fixture: the power type's base-moveset " + name + " is not another instance of the user's own");
            EntityActionType type = (EntityActionType) (this == SERVER ? own : base);
            EntityActionInstance action = type.createActionObj();
            type.initActionFromConfig(action, scene.helper.getLevel(), scene.owner, scene.stand);
            action.setStartingPhase();
            LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            LivingComponentAction.getComponent(scene.stand).setAction(action, scene.owner, SyncType.NO_SYNC);
            scene.helper.assertTrue(scene.stand.getCurStandAction() == action,
                    "fixture: " + name + " of " + side + " did not become D4C's action");
            return action;
        }
    }
}

package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;

/**
 * 1.16 built D4C's special actions (ai/ah.java :74-86) with the StandEntityAction builder's standRecoveryDuration 0 and
 * no getStandRecoveryTicks override (only the hand chop, aa/aa/aa.java :69, and the rapid punch, aa/aa/ac.java :169,
 * had one): once one had performed its task was over, so nothing held the next press back.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CNoRecoveryGameTests {
    private static final List<String> SPECIALS = List.of(
            "summon_player_minion", "annihilation_mark", "annihilation", "flag_parallel_world_travel",
            "swap_with_minion", "love_train", "love_train_deactivate", "love_train_light_wall",
            "love_train_light_wall_deactivate", "minion_ai_control", "pullin_parallel_world_travel", "d4c_item_throw");

    private D4CNoRecoveryGameTests() {}

    @GameTest(template = "empty", batch = "d4cSpecialsNoRecovery", timeoutTicks = 100)
    public static void specialActionsHaveNoRecoveryAsIn116(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-no-recovery")) {
            for (String name : SPECIALS) {
                EntityActionType type = scene.actionType(name);
                EntityActionInstance configured = type.createActionObj();
                type.initActionFromConfig(configured, helper.getLevel(), scene.owner, scene.stand);
                float recovery = configured.phasesLength.getFloat(ActionPhase.RECOVERY);
                if (recovery != 0.0F) {
                    wrong.add(name + " recovery " + recovery);
                }
            }

            // a click: the item throw, then a punch at once
            scene.owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK, 2));
            if (!scene.click("d4c_item_throw", (short) 241)) {
                wrong.add("fixture: the item throw did not start " + scene.keys());
            }
            else {
                EntityActionInstance thrown = scene.stand.getCurStandAction();
                runPastPerform(scene, thrown);
                if (!over(scene, thrown)) {
                    wrong.add("the item throw still holds D4C after its perform (phase " + thrown.getPhase() + ")");
                }
                if (!scene.click("d4c_punch", (short) 242)) {
                    wrong.add("a punch pressed after the item throw had performed did not start");
                }
                LivingComponentAction.getComponent(scene.stand).setAction(null, SyncType.NO_SYNC);
            }

            // a hold to fire: the minion summon fires by itself at 13 ticks
            EntityActionInstance summon = scene.pressAndHold("summon_player_minion", (short) 243);
            if (summon == null) {
                wrong.add("fixture: " + scene.fixtureFailure);
            }
            else {
                for (int tick = 0; tick < 30 && summon.getPhase() == ActionPhase.BUTTON_CHARGE
                        && !summon.isOver(); tick++) {
                    serverTick(scene);
                }
                runPastPerform(scene, summon);
                if (scene.around(D4CMinionEntity.class).isEmpty()) {
                    wrong.add("fixture: the summon did not fire (phase " + summon.getPhase() + ")");
                }
                if (!over(scene, summon)) {
                    wrong.add("the minion summon still holds D4C after it fired (phase " + summon.getPhase() + ")");
                }
                if (!scene.click("d4c_punch", (short) 244)) {
                    wrong.add("a punch pressed after the minion summon had fired did not start");
                }
            }
            scene.around(D4CMinionEntity.class).forEach(Entity::discard);
            scene.around(ItemEntity.class).forEach(Entity::discard);
        }
        helper.assertTrue(wrong.isEmpty(),
                "1.16's D4C specials had no recovery and gave the next press way once they had performed: " + wrong);
        helper.succeed();
    }

    private static void runPastPerform(D4CGameTestScene scene, EntityActionInstance action) {
        for (int tick = 0; tick < 5 && action.getPhase() == ActionPhase.PERFORM && !action.isOver(); tick++) {
            serverTick(scene);
        }
    }

    // D4C's action tick as the server runs it, which clears an action that is over
    private static void serverTick(D4CGameTestScene scene) {
        LivingComponentAction.getComponent(scene.stand).tick();
    }

    private static boolean over(D4CGameTestScene scene, EntityActionInstance action) {
        return action.isOver() || scene.stand.getCurStandAction() != action;
    }
}

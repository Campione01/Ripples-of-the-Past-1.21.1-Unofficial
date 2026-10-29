package rotp.addons.kingcrimson.gametest;

import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.CHOP;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.EYEJAB;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.GROUND_PUNCH;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.IMPALE;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.base;
import static rotp.addons.kingcrimson.gametest.KingCrimsonHeavyStrikeGameTests.prepare;

import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 StandEntityTask.moveToPhase asked the action for each phase's length when that phase started: the light
 * punch's PERFORM and RECOVERY from the current finisher meter, a heavy attack's (and Chop's and Impale's) RECOVERY
 * from the current attack speed and the last heavy finisher value.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonPhaseEntryGameTests {
    private KingCrimsonPhaseEntryGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "kc_phase_entry")
    public static void kingCrimsonPhasesAreReadWhenTheyStart(GameTestHelper helper) {
        KingCrimsonGroundGameTestFixture.identity(helper, KingCrimsonPhaseEntryGameTests.class);
        var checks = new KingCrimsonGroundGameTestFixture.Checks();

        try (var scene = new KingCrimsonGroundGameTestFixture(helper, "E1/punch", 1)) {
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8);
            EntityActionInstance action = press(scene, "punch", "punch", 0.75F);
            float initialPerform = action.phasesLength.getFloat(ActionPhase.PERFORM);
            run(scene, action, phase -> {
                if (phase == ActionPhase.WINDUP) scene.stand.setFinisherMeter(0);
            }, ActionPhase.PERFORM);
            float perform = StandStatFormulas.getLightAttackRecovery(scene.stand.getAttackSpeed(), scene.stand.getFinisherMeter());
            checks.check(action.getPhase() == ActionPhase.PERFORM && action.getCurPhaseLength() == perform && perform != initialPerform,
                    "E1/punch PERFORM read at its start=" + action.getCurPhaseLength() + " expected=" + perform + " initial=" + initialPerform);
            float initialRecovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
            run(scene, action, phase -> {}, ActionPhase.RECOVERY);
            float recovery = StandStatFormulas.getLightAttackRecovery(scene.stand.getAttackSpeed(), scene.stand.getFinisherMeter()) * 4;
            checks.check(action.getPhase() == ActionPhase.RECOVERY && action.getCurPhaseLength() == recovery && recovery != initialRecovery,
                    "E1/punch RECOVERY read at its start=" + action.getCurPhaseLength() + " expected=" + recovery + " initial=" + initialRecovery);
        }

        try (var scene = new KingCrimsonGroundGameTestFixture(helper, "E2/heavy_punch", 1)) {
            scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(8);
            EntityActionInstance action = press(scene, "heavy_punch", "heavy_punch", 0.3F);
            float initialRecovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
            float lastHeavy = scene.stand.getLastHeavyFinisherValue();
            run(scene, action, phase -> scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(20), ActionPhase.RECOVERY);
            float recovery = StandStatFormulas.getHeavyAttackRecovery(scene.stand.getAttackSpeed(), lastHeavy);
            checks.check(action.getPhase() == ActionPhase.RECOVERY && action.getCurPhaseLength() == recovery && recovery != initialRecovery,
                    "E2/heavy_punch RECOVERY read at its start=" + action.getCurPhaseLength() + " expected=" + recovery
                            + " initial=" + initialRecovery);
        }

        // 1.16 KingCrimsonChop and KingCrimsonImpale override getStandRecoveryTicks with the heavy formula.
        for (String ability : new String[] {GROUND_PUNCH, EYEJAB, CHOP, IMPALE}) {
            boolean light = ability.equals(CHOP) || ability.equals(IMPALE);
            String id = (light ? "E4/" : "E3/") + ability;
            // At speed 30 the heavy recovery floors to 1 for any finisher value; at 20 the last heavy value and the
            // current meter give different lengths.
            int speed = light ? 20 : 30;
            try (var scene = new KingCrimsonGroundGameTestFixture(helper, id, 1)) {
                prepare(scene, ability);
                EntityActionInstance action = press(scene, base(ability), ability, 0.75F);
                float initialRecovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
                run(scene, action, phase -> scene.stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(speed), ActionPhase.RECOVERY);
                float recovery = StandStatFormulas.getHeavyAttackRecovery(scene.stand.getAttackSpeed(),
                        scene.stand.getLastHeavyFinisherValue());
                if (light) {
                    float fromMeter = StandStatFormulas.getHeavyAttackRecovery(scene.stand.getAttackSpeed(),
                            scene.stand.getFinisherMeter());
                    checks.check(fromMeter != recovery, id + " the scene separates the last heavy value (" + recovery
                            + ") from the current meter " + scene.stand.getFinisherMeter() + " (" + fromMeter + ")");
                }
                checks.check(action.getPhase() == ActionPhase.RECOVERY && action.getCurPhaseLength() == recovery && recovery != initialRecovery,
                        id + " RECOVERY read at its start=" + action.getCurPhaseLength() + " expected=" + recovery
                                + " initial=" + initialRecovery + " lastHeavy=" + scene.stand.getLastHeavyFinisherValue());
            }
        }
        checks.finish(helper, "KC-PHASE-ENTRY", 6);
    }

    /** The registered input with the given finisher meter; the fixture's own press always sets 0.75. */
    private static EntityActionInstance press(KingCrimsonGroundGameTestFixture scene, String baseName, String expected, float meter) {
        var cow = scene.cow(scene.origin.add(0, 0, 1.5), 200);
        scene.stand.setFinisherMeter(meter);
        ActionTarget target = new ActionTarget(cow);
        LivingComponentAction.getComponent(scene.user).entityAim.setTarget(target);
        LivingComponentAction.getComponent(scene.stand).entityAim.setTarget(target);
        Ability base = scene.power.getAbility(baseName);
        var selected = scene.power.updateAvailableMoves().getContextVariationContainer(base);
        scene.premise(selected != null && selected.ability.name().equals(expected)
                && AbilityInput.withConditionCheck(selected, scene.user, InputMethod.CLICK), "Registered input " + expected);
        BufferingState buffer = BufferingState.clickOnly();
        AbilityInput.keyPress(KingCrimsonGroundGameTestFixture.INPUT, selected.ability, scene.user, null, InputMethod.CLICK, 0,
                buffer, base.getAbilityId());
        EntityActionInstance action = scene.stand.getCurStandAction();
        scene.premise(buffer.isActionSuccess && action != null && action.ability == selected.ability, "Started " + expected);
        return action;
    }

    /** Ticks the action until it enters the phase; the change runs before each tick of an earlier phase. */
    private static void run(KingCrimsonGroundGameTestFixture scene, EntityActionInstance action,
            Consumer<ActionPhase> beforeTick, ActionPhase until) {
        for (int tick = 0; action.getPhase() != until && !action.isOver() && tick < 200; tick++) {
            beforeTick.accept(action.getPhase());
            action._tickAction();
        }
        scene.premise(action.getPhase() == until, "Reached " + until);
    }
}

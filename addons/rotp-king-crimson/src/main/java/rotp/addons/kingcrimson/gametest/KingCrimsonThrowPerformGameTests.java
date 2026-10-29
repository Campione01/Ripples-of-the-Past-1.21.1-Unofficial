package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowPerformGameTests {
    private KingCrimsonThrowPerformGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 2000, batch = "kc_throw_perform")
    public static void throwPerformUsesLiveDonorTimingWithoutRepeatedEmission(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowPerformGameTests.class);
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowTimingFixture.class);
        var suite = new Timeline(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    private static final class Timeline extends KingCrimsonThrowGameTestFixture.Suite {
        double speed, lastSpeed;
        float meter, lastMeter, windup, recovery, tickStamina;
        ActionPhase before;
        int entryAge, recoveryAge, expectedPerform, expectedRecovery, lastPerformTick, tickSpawns;
        Timeline(GameTestHelper helper) { super(helper, "D-KC-THROW-PERFORM", 20); }
        @Override void prepare(int index) {
            int kind = index % 10; boolean triple = index >= 10;
            speed = kind < 6 ? new double[] {8, 14, 28}[kind / 2] : new double[] {8, 28, 14, 8}[kind - 6];
            meter = kind < 6 ? (kind % 2 == 0 ? 0 : 0.99F) : new float[] {0, 0.99F, 0.99F, 0.99F}[kind - 6];
            scene = new KingCrimsonThrowGameTestFixture(helper, "perform/" + (triple ? "triple/" : "single/") + kind,
                    KingCrimsonThrowGameTestFixture.Mode.FINITE, 600, 73);
            scene.power.setStamina(scene.power.getMaxStamina());
            KingCrimsonThrowTimingFixture.stats(scene, speed, meter);
            scene.premise(scene.stand.getAttackSpeed() == speed, "Actual requested speed without low-stamina debuff");
            scene.hand(new ItemStack(Items.ARROW, 3));
            var press = scene.press(triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE,
                    KingCrimsonThrowGameTestFixture.FIRST);
            scene.premise(press.conditions() && press.admitted() && !press.buffered(), "Unchanged legal registered input");
            windup = scene.action.phasesLength.getFloat(ActionPhase.WINDUP);
            recovery = scene.action.phasesLength.getFloat(ActionPhase.RECOVERY);
            checks.check(windup == StandStatFormulas.getHeavyAttackWindup(speed, meter), scene.id + " preserved heavy WINDUP");
            checks.check(recovery == StandStatFormulas.getHeavyAttackRecovery(speed, scene.stand.getLastHeavyFinisherValue()),
                    scene.id + " preserved heavy RECOVERY");
            entryAge = recoveryAge = -1; lastPerformTick = -1;
            scene.duringWindup = () -> {
                if (kind >= 6) {
                    speed = new double[] {14, 14, 8, 28}[kind - 6];
                    meter = new float[] {0.99F, 0, 0, 0}[kind - 6];
                    KingCrimsonThrowTimingFixture.stats(scene, speed, meter);
                }
            };
            scene.listen(EntityTickEvent.Pre.class, event -> {
                if (event.getEntity() != scene.stand || scene.eventFailure != null) return;
                try {
                    before = scene.action.getPhase();
                    if (before == ActionPhase.WINDUP) {
                        scene.stand.setFinisherMeter(meter);
                        lastSpeed = scene.stand.getAttackSpeed(); lastMeter = scene.stand.getFinisherMeter();
                    }
                    tickStamina = scene.power.getStamina(); tickSpawns = scene.spawns.size();
                    if (before == ActionPhase.PERFORM) {
                        int phaseTick = (int) scene.action.getPhaseTick();
                        checks.check(phaseTick > lastPerformTick, scene.id + " PERFORM clock advances without reset tick=" + phaseTick);
                        lastPerformTick = phaseTick;
                        checks.check(!scene.action.canBeCancelledInto((EntityActionType) scene.power.getAbility("punch")), scene.id + " no early recovery cancellation");
                    }
                } catch (RuntimeException error) { scene.eventFailure = error; }
            });
            scene.listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() != scene.stand || scene.eventFailure != null) return;
                try {
                    var phase = scene.action.getPhase();
                    if (phase == ActionPhase.PERFORM && entryAge < 0) {
                        entryAge = scene.stand.tickCount; expectedPerform = KingCrimsonThrowTimingFixture.donorPerform(lastSpeed, lastMeter);
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.PERFORM) == expectedPerform
                                && scene.action.getAnimPhaseLength() == expectedPerform,
                                scene.id + " actual entry length=" + scene.action.getAnimPhaseLength() + " expected=" + expectedPerform);
                        KingCrimsonThrowGameTestFixture.log("ENTRY " + scene.id + " bodyAge=" + entryAge
                                + " speed=" + lastSpeed + " meter=" + lastMeter + " expected=" + expectedPerform);
                    }
                    if (phase == ActionPhase.PERFORM) {
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.PERFORM) == expectedPerform
                                && scene.action.getAnimPhaseLength() == expectedPerform, scene.id + " PERFORM cached length stays fixed");
                    }
                    if (before == ActionPhase.PERFORM && lastPerformTick > 0) {
                        checks.check(scene.spawns.size() == tickSpawns && scene.power.getStamina() == tickStamina,
                                scene.id + " later PERFORM tick has no emission or debit");
                    }
                    if (scene.performed && phase == ActionPhase.PERFORM) {
                        KingCrimsonThrowTimingFixture.stats(scene, 3, 0.99F);
                    }
                    if (phase == ActionPhase.RECOVERY && recoveryAge < 0) {
                        recoveryAge = scene.stand.tickCount;
                        expectedRecovery = KingCrimsonThrowTimingFixture.donorRecovery(
                                scene.stand.getAttackSpeed(), scene.stand.getLastHeavyFinisherValue());
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.RECOVERY) == expectedRecovery
                                && scene.action.getAnimPhaseLength() == expectedRecovery,
                                scene.id + " measured first RECOVERY entry uses donor heavy oracle");
                        checks.check(recoveryAge - entryAge == expectedPerform,
                                scene.id + " natural PERFORM duration=" + (recoveryAge - entryAge) + " expected=" + expectedPerform);
                        checks.check(scene.action.canBeCancelledInto((EntityActionType) scene.power.getAbility("punch")), scene.id + " recovery cancellation restored at correct time");
                        KingCrimsonThrowTimingFixture.stats(scene, 28, 0.99F);
                        scene.stand.setHeavyPunchFinisher();
                    }
                    if (phase == ActionPhase.RECOVERY && recoveryAge >= 0) {
                        checks.check(scene.action.phasesLength.getFloat(ActionPhase.RECOVERY) == expectedRecovery
                                && scene.action.getAnimPhaseLength() == expectedRecovery,
                                scene.id + " RECOVERY entry length stays frozen after later stat changes");
                    }
                } catch (RuntimeException error) { scene.eventFailure = error; }
            });
        }
        @Override boolean verify() {
            if (recoveryAge < 0 || scene.stand.tickCount < recoveryAge + 2) return false;
            scene.premise(scene.windupChanged && scene.performed && entryAge > scene.inputStandAge, "Natural full timeline");
            scene.checkPayment(checks, true);
            int count = scene.ability.name().equals(KingCrimsonThrowGameTestFixture.TRIPLE) ? 3 : 1;
            checks.check(scene.spawns.size() == count && scene.postAmmo == scene.preAmmo - count,
                    scene.id + " exactly one throw batch with expected ammo");
            checks.check(scene.preCooldown > 0 && scene.postCooldown == scene.preCooldown,
                    scene.id + " perform does not move/reset admission cooldown");
            checks.check(scene.action.phasesLength.getFloat(ActionPhase.WINDUP) == windup, scene.id + " heavy WINDUP length untouched by live stats");
            checks.check(scene.action.phasesLength.getFloat(ActionPhase.RECOVERY) == expectedRecovery,
                    scene.id + " measured RECOVERY entry length remains frozen");
            return true;
        }
        @Override void release() {}
    }
}

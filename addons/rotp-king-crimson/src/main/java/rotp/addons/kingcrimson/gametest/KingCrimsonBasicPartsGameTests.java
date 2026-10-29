package rotp.addons.kingcrimson.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonBasicPartsGameTests {
    private KingCrimsonBasicPartsGameTests() {}
    private record Spec(String id, String base, String selected, boolean missingBody, boolean missingArms,
            boolean armsOnly, boolean absent, boolean grab, float finisher, boolean admitted) {}
    private static final List<Spec> CASES = List.of(
            new Spec("full-heavy", "heavy_punch", "heavy_punch", false, false, false, false, false, 0, true),
            new Spec("heavy-no-body", "heavy_punch", "heavy_punch", true, false, false, false, false, 0, false),
            new Spec("heavy-no-arms", "heavy_punch", "heavy_punch", false, true, false, false, false, 0, true),
            new Spec("heavy-no-both", "heavy_punch", "heavy_punch", true, true, false, false, false, 0, false),
            new Spec("punch-no-body", "punch", "punch", true, false, false, false, false, 0, true),
            new Spec("punch-no-arms", "punch", "punch", false, true, false, false, false, 0, false),
            new Spec("grab-heavy-no-body", "heavy_punch", "grab_heavy_punch", true, false, false, false, true, 0, true),
            new Spec("grab-heavy-no-arms", "heavy_punch", "grab_heavy_punch", false, true, false, false, true, 0, false),
            new Spec("arms-ground-no-body", "heavy_punch", "kingcrimson_groundslam", true, false, true, false, false, 0, true),
            new Spec("arms-ground-no-arms", "heavy_punch", "kingcrimson_groundslam", false, true, true, false, false, 0, false),
            new Spec("absent-ground", "heavy_punch", "kingcrimson_groundslam", false, false, false, true, false, 0, true),
            new Spec("absent-ground-no-arms", "heavy_punch", "kingcrimson_groundslam", false, true, false, true, false, 0, false),
            new Spec("full-ground-finisher", "heavy_punch", "kingcrimson_groundpunch", false, false, false, false, false, 0.75F, true));

    @GameTest(template="empty",timeoutTicks=2200,batch="kc_basic_parts")
    public static void ordinaryHeavyRequiresMainBodyWithoutArmsHardening(GameTestHelper helper) {
        KingCrimsonBasicMeleeFixture.identity(helper, KingCrimsonBasicPartsGameTests.class);
        Suite suite = new Suite(helper);
        try { suite.start(); }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    private static final class Suite implements GameTestListener, AutoCloseable {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        KingCrimsonGroundAdmissionFixture scene;
        KingCrimsonGroundAdmissionFixture.Actor actor;
        Cow target;
        StandEntity performer;
        EntityActionInstance action;
        Spec spec;
        int index, checks, ticks, startAge;
        boolean closed, done, actualAdmitted;
        RuntimeException eventFailure;
        Suite(GameTestHelper helper) { this.helper = helper; }
        void start() { helper.testInfo.addListener(this); helper.onEachTick(this::tick); }
        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (done) { scene.close(); scene = null; done = false; }
                if (scene == null) {
                    if (index == CASES.size()) {
                        close(); log("D-KC-BASIC-03 SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        helper.assertTrue(failures.isEmpty(), "D-KC-BASIC-03 behavioral failures=" + failures.size()); helper.succeed(); return;
                    }
                    prepare(CASES.get(index++)); ticks = 0;
                }
                helper.assertTrue(++ticks < 130, "Parts native scene deadline " + spec.id);
                if (!actualAdmitted && ticks == 5) {
                    check(scene.hits(target).isEmpty() && actor.power().getSummonedStandEntity() == (spec.absent ? null : actor.stand()), spec.id + " refused input has no impact or auto-summon");
                    done = true;
                }
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }
        void prepare(Spec value) {
            spec = value; action = null;
            scene = new KingCrimsonGroundAdmissionFixture(helper, "parts/" + spec.id, 1);
            actor = scene.actor(scene.origin, scene.origin, 0, 4, 40);
            target = scene.cow(scene.origin.add(0, 0, 1.5));
            var instance = actor.power().getStandInstance().orElseThrow();
            if (spec.missingBody) scene.premise(instance.removePart(StandPart.MAIN_BODY), "Actual MAIN_BODY removed");
            if (spec.missingArms) scene.premise(instance.removePart(StandPart.ARMS), "Actual ARMS removed");
            scene.premise(instance.hasPart(StandPart.MAIN_BODY) != spec.missingBody && instance.hasPart(StandPart.ARMS) != spec.missingArms, "Native missing-part premise");
            actor.stand().setFinisherMeter(spec.finisher);
            if (spec.armsOnly) actor.stand().setArmsOnlyMode(true, true);
            if (spec.grab) actor.stand().getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);
            if (spec.absent) { actor.type().forceUnsummon(actor.user(), actor.power()); scene.premise(!actor.power().isSummoned(), "Actual absent performer before input"); }
            LivingComponentAction.getComponent(actor.user()).entityAim.setTarget(new ActionTarget(target));
            LivingComponentAction.getComponent(actor.stand()).entityAim.setTarget(new ActionTarget(target));
            var base = actor.power().getAbility(spec.base);
            var selected = actor.power().updateAvailableMoves().getContextVariationContainer(base);
            scene.premise(selected != null && selected.ability == actor.power().getAbility(spec.selected), "Fresh exact ordinary/grab/Ground context selection " + spec.selected);
            if (spec.grab) scene.premise(selected.ability != base, "Distinct grab factory remains selected");
            actualAdmitted = AbilityInput.withConditionCheck(selected, actor.user(), InputMethod.CLICK);
            check(actualAdmitted == spec.admitted, spec.id + " fresh registered admission=" + actualAdmitted + " expected=" + spec.admitted);
            BufferingState buffer = BufferingState.clickOnly();
            var input = actualAdmitted ? AbilityInput.keyPress(KingCrimsonGroundAdmissionFixture.ATTACK, selected.ability, actor.user(), null, InputMethod.CLICK, 0, buffer, base.getAbilityId()) : null;
            check(buffer.isActionSuccess == actualAdmitted, spec.id + " actual keyPress agrees with fresh conditions=" + buffer.isActionSuccess);
            performer = actor.power().getSummonedStandEntity();
            if (!actualAdmitted) { check(input == null && (performer == null || performer.getCurStandAction() == null), spec.id + " refused input creates no action"); return; }
            scene.premise(performer != null && input != null && input.action instanceof EntityActionInstance, "Real admitted input creates native performer/action");
            action = (EntityActionInstance) input.action;
            scene.premise(action.ability == selected.ability && action.getPerformer() == performer, "Actual action belongs to selected registration and current performer");
            if (performer != actor.stand()) { scene.owned.add(performer); performer.setCanFollowUser(false); performer.setNoGravity(true); }
            startAge = performer.tickCount;
            log("INPUT " + spec.id + " selected=" + spec.selected + " actual=" + actualAdmitted + " performer=" + performer.getId() + " parts=" + instance.getAllParts());
            scene.listen(EntityTickEvent.Post.class, event -> {
                if (event.getEntity() != performer || done || action.punchedTarget == null) return;
                try { impact(); } catch (RuntimeException error) { eventFailure = error; }
            });
        }
        void impact() {
            scene.premise(performer.tickCount > startAge && action.punchedTarget.getMainEntity() == target, "Natural same-action primary impact");
            check(!scene.hits(target).isEmpty(), spec.id + " admitted action reaches actual virtual native hurt");
            check(scene.hits(target).stream().allMatch(hit -> hit.source.getDirectEntity() == performer
                    && (hit.source.getEntity() == performer || hit.source.getEntity() == actor.user())), spec.id + " actual damage source retains Stand/user ownership");
            done = true;
        }
        void check(boolean passed, String detail) { checks++; log((passed ? "PASS " : "FAIL ") + detail); if (!passed) failures.add(detail); }
        @Override public void close() { if (closed) return; closed = true; if (scene != null) { if (actor != null) actor.stand().getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null); scene.close(); } }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
    private static void log(String message) { KingCrimsonBasicMeleeFixture.log(message); }
}

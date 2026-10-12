package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightComboTimingGameTests {
    private static final short ATTACK = 125;
    private static final short RELEASE = 126;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(JojoMod.resLoc("light_combo_timing_test"));
    private static Suite active;

    private LightComboTimingGameTests() {}
    private enum Kind { CYCLE, BUFFER_WINDUP, BUFFER_PERFORM, SKIP, LOCK_NEXT, LOCK_PREVIOUS, SAME_OBJECT, NATURAL, HEAVY, GRAB, FINISHER, TS_PUNCH }
    private record Spec(String stand, Kind kind) {}

    @GameTest(template = "empty", timeoutTicks = 1400, batch = "light_combo_timing")
    public static void registeredLightFamiliesPreserveContinuationTiming(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            log("IDENTITY punch=" + sha(StandEntityPunchAbility.class) + " fixture=" + sha(LightComboTimingGameTests.class));
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = "light_combo_timing")
    public static void releaseComboBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> specs = new ArrayList<>();
        for (String stand : List.of("star_platinum", "the_world")) {
            for (Kind kind : List.of(Kind.CYCLE, Kind.BUFFER_WINDUP, Kind.BUFFER_PERFORM, Kind.SKIP)) specs.add(new Spec(stand, kind));
        }
        for (Kind kind : List.of(Kind.LOCK_NEXT, Kind.LOCK_PREVIOUS, Kind.NATURAL, Kind.HEAVY, Kind.GRAB, Kind.FINISHER)) specs.add(new Spec("star_platinum", kind));
        specs.add(new Spec("the_world", Kind.SAME_OBJECT));
        specs.add(new Spec("the_world", Kind.TS_PUNCH));
        return specs;
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<Spec> specs = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        RuntimeException observerFailure;
        int index;
        int checks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping light combo suites");
            this.helper = helper; level = helper.getLevel();
            helper.testInfo.addListener(this); active = this;
        }
        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed && observerFailure == null && event.getEntity() == scene.stand) {
                    try { scene.pre(); } catch (RuntimeException error) { observerFailure = error; }
                }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed && observerFailure == null && event.getEntity() == scene.stand) {
                    try { scene.post(); } catch (RuntimeException error) { observerFailure = error; }
                }
            };
            listeners.add(pre); listeners.add(post);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }
        void tick() {
            if (closed) return;
            try {
                if (observerFailure != null) throw observerFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "Light combo native timing failed; see SUMMARY");
                        helper.succeed(); return;
                    }
                    scene = new Scene(this, specs.get(index++)); scene.prepare();
                }
                helper.assertTrue(++scene.worldTicks < 160, "Native combo lifecycle did not finish: " + scene.id());
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }
        void check(boolean passed, String detail) {
            checks++; log((passed ? "PASS " : "FAIL ") + scene.id() + " " + detail);
            if (!passed) failures.add(scene.id() + ": " + detail);
        }
        void close() {
            if (closed) return; closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear(); if (scene != null) scene.close();
            if (active == this) active = null;
            log("CLEANUP listeners=0");
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        Player user;
        StandPower power;
        StandType type;
        StandEntity stand;
        Cow target;
        EntityActionInstance prefix;
        EntityActionInstance watch;
        EntityActionInstance before;
        ActionPhase lastWatchPhase;
        float beforeTicks;
        float initialSkip;
        int expectedWindup;
        int nativeTicks;
        int actionIndex;
        int stage;
        int worldTicks;
        boolean queued;
        boolean replayed;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.level; }
        String id() { return spec.stand + "/" + spec.kind; }
        boolean buffered() { return spec.kind == Kind.BUFFER_WINDUP || spec.kind == Kind.BUFFER_PERFORM; }
        String[] sequence() {
            if (spec.kind == Kind.LOCK_NEXT) return new String[] {"punch", "punch3", "punch4", "punch"};
            if (spec.kind == Kind.SAME_OBJECT || spec.kind == Kind.NATURAL) return new String[] {"punch", "punch"};
            if (spec.kind == Kind.CYCLE || spec.kind == Kind.LOCK_PREVIOUS) return spec.stand.equals("star_platinum")
                    ? new String[] {"punch", "punch2", "punch3", "punch4", "punch"} : new String[] {"punch", "punch2", "punch"};
            return new String[] {"punch", "punch2"};
        }
        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 96,
                    chunk.getMiddleBlockZ() + 0.5);
            premise(origin.y < level.getMaxBuildHeight() - 8, "bounded elevated native area");
            user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            user.setNoGravity(true); user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            premise(level.addFreshEntity(user), "native Survival owner added");
            power = PowerClass.STAND.attachGet(user);
            type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(spec.stand));
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "registered Stand granted");
            power.setResolveLevel(2);
            premise(type.summon(user, power), "registered full Stand summon");
            stand = power.getSummonedStandEntity(); premise(stand != null && stand.getUser() == user, "native owner binding");
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true); stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            power.setStamina(power.getMaxStamina()); stats();
            unlock("punch");
            premise(power.getAbility("punch").abilityType == ModStandAbilities.PUNCH.get(), "registered COMBAT/PUNCH root");
            if (spec.kind == Kind.LOCK_NEXT || spec.kind == Kind.SAME_OBJECT) {
                power.getCurTypeData()._lockedAbilities.add("punch2");
                suite.check(!power.getAbility("punch2").isAbilityAvailable(power), "real unavailable member remains registered");
            }
            if (spec.kind == Kind.HEAVY) {
                power.getCurTypeData()._lockedAbilities.add("finisher_uppercut");
                suite.check(!power.getAbility("finisher_uppercut").isAbilityAvailable(power), "real finisher unavailable for ordinary-heavy reset control");
                stand.setFinisherMeter(0.5F);
                prefix = click("heavy_punch", ATTACK, 0, false);
                suite.check(prefix.ability == power.getAbility("heavy_punch")
                        && prefix.getClass() == StandEntityHeavyPunchAbility.StandEntityHeavyPunch.class, "actual ordinary heavy class, not a fabricated light-family predecessor");
            }
            else if (spec.kind == Kind.FINISHER) {
                power.setResolveLevel(0); unlock("uppercut");
                suite.check(!power.getAbility("finisher_uppercut").isAbilityAvailable(power), "real Resolve0 finisher refusal");
                power.setResolveLevel(1); unlock("uppercut");
                premise(power.getAbility("finisher_uppercut").isAbilityAvailable(power), "actual unlocked finisher skill/Resolve1");
                stand.setFinisherMeter(0.75F);
                prefix = click("heavy_punch", ATTACK, 0, false);
                suite.check(prefix.ability == power.getAbility("finisher_uppercut")
                        && power.getAbility("finisher_uppercut").abilityType == ModStandAbilities.HEAVY_UPPERCUT.get(), "registered finisher routing stays outside light family");
            }
            else if (spec.kind == Kind.GRAB) {
                unlock("grab_throw");
                target = EntityType.COW.create(level); premise(target != null, "real native grab target");
                target.setNoAi(true); target.setNoGravity(true); target.setInvulnerable(true);
                target.moveTo(origin.x, origin.y, origin.z + 1.5);
                premise(level.addFreshEntity(target), "living native grab target added");
                aim(target); prefix = click("grab", ATTACK, 0, false);
            }
            else {
                if (spec.kind == Kind.TS_PUNCH) {
                    unlock("ts_punch");
                    suite.check(!power.getAbility("ts_punch").isAbilityAvailable(power), "real Resolve2 TS-punch refusal");
                    power.setResolveLevel(3); unlock("ts_punch");
                    premise(power.getAbility("ts_punch").isAbilityAvailable(power), "actual unlocked TS-punch skill/Resolve3");
                }
                beginLight("punch", 1, 0);
                if (spec.kind == Kind.BUFFER_WINDUP) queue();
            }
            log("START " + id() + " speed=" + stand.getAttackSpeed() + " counter=" + stand.guardCounter());
        }
        void stats() {
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
            StandEntity.updateStandStatAttributes(stand, user); stand.updateStrengthMultipliers();
            premise(stand.getAttackSpeed() == 16 && stand.guardCounter() == 0, "deterministic native speed16/counter0 actual="
                    + stand.getAttackSpeed() + "/" + stand.guardCounter() + " stamina=" + power.getStamina() + " efficiency=" + stand.getStandEfficiency());
        }
        void pre() {
            EntityActionInstance current = stand.getCurStandAction();
            if (buffered() && queued && !replayed && current != null && current != watch) {
                suite.check(lastWatchPhase == ActionPhase.RECOVERY, "buffer replay follows real predecessor RECOVERY");
                premise(current.ability == power.getAbility("punch2"), "actual replay resolves registered second member");
                replayed = true; actionIndex = 1; record(current, "punch2", 2, 0);
            }
            before = stand.getCurStandAction();
            beforeTicks = before != null ? before.getFullTicksPassed() : 0;
        }
        void post() {
            if (closed || done) return;
            if (before == watch && watch != null && stand.getCurStandAction() == watch) {
                float delta = watch.getFullTicksPassed() - beforeTicks;
                if (delta > 0) {
                    nativeTicks++;
                    if (nativeTicks <= 2) {
                        suite.check(delta == 1, "actual native action-clock increment=" + delta + " sample=" + nativeTicks);
                        if (expectedWindup == 1 && nativeTicks == 1) suite.check(watch.getPhase() == ActionPhase.PERFORM
                                && watch.getPhaseTick() == 0, "first/scope-reset punch enters PERFORM on native action tick1");
                        if (expectedWindup == 2 && nativeTicks == 1) suite.check(watch.getPhase() == ActionPhase.WINDUP
                                && watch.getPhaseTick() == 1 + initialSkip, "continuation still WINDUP after native action tick1, actual="
                                        + watch.getPhase() + "/" + watch.getPhaseTick());
                        if (expectedWindup == 2 && nativeTicks == 2) suite.check(watch.getPhase() == ActionPhase.PERFORM
                                && watch.getPhaseTick() == initialSkip, "continuation enters PERFORM on native action tick2, actual="
                                        + watch.getPhase() + "/" + watch.getPhaseTick());
                    }
                }
                lastWatchPhase = watch.getPhase();
            }
            if (spec.kind == Kind.HEAVY || spec.kind == Kind.FINISHER) {
                if (watch == null && prefix.getPhase() == ActionPhase.RECOVERY) {
                    suite.check(prefix.ability != power.getAbility("punch") && !prefix.isOver(), "real unrelated predecessor remains installed during initialization");
                    beginLight("punch", 1, 0);
                }
            }
            else if (spec.kind == Kind.GRAB) grab();
            else if (spec.kind == Kind.TS_PUNCH) tsPunch();
            else if (buffered()) {
                if (!queued && watch.getPhase() == ActionPhase.PERFORM) queue();
            }
            else if (spec.kind != Kind.NATURAL && watch.getPhase() == ActionPhase.RECOVERY && actionIndex + 1 < sequence().length) {
                if (spec.kind == Kind.LOCK_PREVIOUS && actionIndex == 1) {
                    power.getCurTypeData()._lockedAbilities.add("punch2");
                    suite.check(!power.getAbility("punch2").isAbilityAvailable(power), "live predecessor now locked, registration-family identity retained");
                }
                String next = sequence()[++actionIndex];
                beginLight(next, 2, spec.kind == Kind.SKIP ? 8 : 0);
            }
            if (watch != null && watch.isOver() && stand.getCurStandAction() == null) {
                if (spec.kind == Kind.NATURAL && actionIndex == 0) {
                    suite.check(true, "native complete/clear precedes fresh combo reset"); actionIndex = 1; beginLight("punch", 1, 0);
                }
                else if (finishedSequence()) {
                    suite.check(nativeTicks >= 2 && stand.getCurStandAction() == null, "natural completion/cleanup, no forced phase or clock"); done = true;
                }
            }
        }
        boolean finishedSequence() {
            if (spec.kind == Kind.GRAB) return stage == 4;
            if (spec.kind == Kind.TS_PUNCH) return stage == 2;
            if (spec.kind == Kind.HEAVY || spec.kind == Kind.FINISHER) return true;
            if (buffered()) return replayed;
            return actionIndex + 1 == sequence().length;
        }
        void grab() {
            if (stage == 0 && prefix.isOver() && stand.getCurStandAction() == null) {
                premise(LivingComponentGrab.getEntityGrabbedBy(stand) == target, "actual registered grab caught living target");
                stage = 1; beginLight("grab_punch", 1, 0);
            }
            else if (stage == 1 && watch.getPhase() == ActionPhase.RECOVERY) {
                stage = 2; beginLight("grab_punch", 2, 0);
            }
            else if (stage == 2 && watch.getPhase() == ActionPhase.RECOVERY) {
                EntityActionInstance previous = watch;
                click("grab_release", RELEASE, 0, false);
                suite.check(LivingComponentGrab.getEntityGrabbedBy(stand) == null && stand.getCurStandAction() == previous
                        && !previous.isOver(), "actual registered release leaves live GRAB predecessor for COMBAT reset");
                stage = 3;
            }
            else if (stage == 3 && watch.getPhase() == ActionPhase.RECOVERY) {
                // The native user tick refreshes the per-tick context after the registered release.
                stage = 4; beginLight("punch", 1, 0);
            }
        }
        void tsPunch() {
            if (stage == 0 && watch.getPhase() == ActionPhase.RECOVERY && canClick("ts_punch")) {
                stand.setFinisherMeter(0.75F);
                prefix = click("ts_punch", ATTACK, 0, false);
                suite.check(prefix.ability == power.getAbility("ts_punch") && power.getAbility("ts_punch").abilityType != ModStandAbilities.PUNCH.get()
                        && prefix.phasesLength.getFloat(ActionPhase.WINDUP) == 5, "registered TS-punch retains separate type/WINDUP5 routing");
                watch = null; stage = 1;
            }
            else if (stage == 1 && prefix.isOver() && stand.getCurStandAction() == null) {
                stage = 2; beginLight("punch", 1, 0);
            }
        }
        void beginLight(String selected, int windup, float skip) {
            EntityActionInstance previous = stand.getCurStandAction();
            if (previous != null) premise(previous.getPhase() == ActionPhase.RECOVERY && !previous.isOver(), "genuine live RECOVERY predecessor");
            EntityActionInstance current = click("punch", ATTACK, skip, false);
            premise(current != null && current != previous, "actual new registered action installed");
            record(current, selected, windup, skip > 0 ? 0.5F : 0);
        }
        void record(EntityActionInstance current, String selected, int windup, float expectedSkip) {
            watch = current; expectedWindup = windup; initialSkip = expectedSkip; nativeTicks = 0;
            premise(current.ability == power.getAbility(selected), "registered selected member identity " + selected);
            Ability ability = (Ability) current.ability;
            suite.check(ability.abilityType == ModStandAbilities.PUNCH.get()
                    && ability.usageGroup == (selected.equals("grab_punch") ? AbilityUsageGroup.GRAB : AbilityUsageGroup.COMBAT), "real registered light/grab type scope");
            suite.check(current.phasesLength.getFloat(ActionPhase.WINDUP) == windup, "stored WINDUP="
                    + current.phasesLength.getFloat(ActionPhase.WINDUP) + " expected=" + windup + " selected=" + selected);
            suite.check(current.getPhase() == ActionPhase.WINDUP && current.getPhaseTick() == expectedSkip,
                    "actual initialized phase/skip=" + current.getPhase() + "/" + current.getPhaseTick() + " expectedSkip=" + expectedSkip);
            log("INSTALL " + id() + " selected=" + selected + " action=" + current.id + " nativeFullTicks=" + current.getFullTicksPassed()
                    + " storedWindup=" + current.phasesLength.getFloat(ActionPhase.WINDUP) + " phaseTick=" + current.getPhaseTick());
        }
        void queue() {
            ActionPhase phase = watch.getPhase();
            premise(phase == (spec.kind == Kind.BUFFER_WINDUP ? ActionPhase.WINDUP : ActionPhase.PERFORM), "real phase for buffered click");
            EntityActionInstance previous = watch;
            click("punch", ATTACK, 8, true);
            suite.check(stand.getCurStandAction() == previous && !previous.isOver(), "real buffered input leaves WINDUP/PERFORM predecessor installed");
            queued = true;
        }
        EntityActionInstance click(String name, short key, float skip, boolean buffer) {
            stats(); AbilityInput.keyRelease(key, user);
            Ability base = power.getAbility(name); premise(base != null, "registered ability " + name);
            var selected = power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK), "actual condition/context admission " + name);
            BufferingState state = buffer ? BufferingState.clickCanBuffer() : BufferingState.clickOnly();
            AbilityInput.keyPress(key, selected.ability, user, null, InputMethod.CLICK, skip, state, base.getAbilityId());
            premise(buffer ? state.shouldBuffer && !state.isActionSuccess : state.isActionSuccess, "actual registered click/buffer outcome " + name);
            log("CLICK " + id() + " base=" + name + " selected=" + selected.ability.name() + " skipArgument=" + skip + " buffered=" + state.shouldBuffer);
            return stand.getCurStandAction();
        }
        boolean canClick(String name) {
            var selected = power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
            return selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK);
        }
        void aim(Cow cow) {
            ActionTarget target = new ActionTarget(cow);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
            LivingComponentAction.getComponent(user).entityAim.setTarget(target);
            user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
            stand.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
        }
        void unlock(String skill) {
            PowerSkillUnlocks.Result result = UNLOCKS.forceUnlock(power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public declared skill unlock " + skill);
        }
        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, id() + " premise: " + detail); }
        @Override public void close() {
            if (closed) return; closed = true;
            if (user != null) { AbilityInput.keyRelease(ATTACK, user); AbilityInput.keyRelease(RELEASE, user); }
            if (stand != null) stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
            if (power != null && power.isSummoned()) type.forceUnsummon(user, power);
            if (target != null) target.discard();
            if (user != null) user.discard();
        }
    }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("Native class binding failed " + type.getName(), error); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-MELEE-05 " + message); }
}

package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility.TheWorldHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.resolve.ResolveModeEffect;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.timestop.TimeStopLearning;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TheWorldTSPunchParityGameTests {
    private static final short FIRST = 151;
    private static final short TS = 152;
    private static final short FOLLOW = 153;
    private static final String BATCH = "the_world_ts_punch_parity";
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(TheWorldTSPunchParityGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;

    private TheWorldTSPunchParityGameTests() {}

    private enum Kind {
        BASELINE, LOW_AFTER_BLINK, ZERO, PAID, RESOLVE, CREATIVE, IDLE_RESERVE,
        HEAVY_RECOVERY, LIGHT_RECOVERY, HEAVY_WINDUP, HEAVY_PERFORM, LIGHT_WINDUP, LIGHT_PERFORM,
        OUT_LIGHT_NOW, OUT_LIGHT_QUEUED, SAME_TS, COOLDOWN, HEAVY_CONTROL
    }

    private record Spec(Kind kind, float meter, boolean empty) {
        String id() { return kind + "-M" + meter + (empty ? "-MISS" : "-HIT"); }
        boolean creative() {
            return kind == Kind.CREATIVE || kind == Kind.IDLE_RESERVE || kind == Kind.SAME_TS
                    || kind == Kind.OUT_LIGHT_NOW || kind == Kind.OUT_LIGHT_QUEUED;
        }
        boolean predecessor() {
            return kind == Kind.HEAVY_RECOVERY || kind == Kind.LIGHT_RECOVERY || deniedPhase();
        }
        boolean lightPredecessor() {
            return kind == Kind.LIGHT_RECOVERY || kind == Kind.LIGHT_WINDUP || kind == Kind.LIGHT_PERFORM;
        }
        boolean deniedPhase() {
            return kind == Kind.HEAVY_WINDUP || kind == Kind.HEAVY_PERFORM
                    || kind == Kind.LIGHT_WINDUP || kind == Kind.LIGHT_PERFORM;
        }
        ActionPhase predecessorPhase() {
            if (kind == Kind.HEAVY_WINDUP || kind == Kind.LIGHT_WINDUP) return ActionPhase.WINDUP;
            if (kind == Kind.HEAVY_PERFORM || kind == Kind.LIGHT_PERFORM) return ActionPhase.PERFORM;
            return ActionPhase.RECOVERY;
        }
        boolean reserve() { return kind == Kind.IDLE_RESERVE || kind == Kind.HEAVY_RECOVERY || kind == Kind.LIGHT_RECOVERY; }
        boolean outgoing() { return kind == Kind.OUT_LIGHT_NOW || kind == Kind.OUT_LIGHT_QUEUED || kind == Kind.SAME_TS; }
    }

    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}
    private record Press(boolean admitted, boolean success, boolean buffered, EntityActionInstance action) {}

    @GameTest(template = "empty", timeoutTicks = 2800, batch = BATCH)
    public static void registeredTsPunchKeepsItsOwnDonorTaskRules(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            suite.identity();
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = BATCH)
    public static void releaseTsPunchParityBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> result = new ArrayList<>();
        for (float meter : new float[] {0, 0.75F, 1}) {
            result.add(new Spec(Kind.BASELINE, meter, false));
            result.add(new Spec(Kind.BASELINE, meter, true));
        }
        for (Kind kind : new Kind[] {Kind.LOW_AFTER_BLINK, Kind.ZERO, Kind.PAID, Kind.RESOLVE, Kind.CREATIVE})
            result.add(new Spec(kind, 0, false));
        result.add(new Spec(Kind.IDLE_RESERVE, 0, true));
        result.add(new Spec(Kind.HEAVY_RECOVERY, 0, true));
        result.add(new Spec(Kind.HEAVY_RECOVERY, 0.75F, true));
        result.add(new Spec(Kind.LIGHT_RECOVERY, 0, true));
        for (Kind kind : new Kind[] {Kind.HEAVY_WINDUP, Kind.HEAVY_PERFORM, Kind.LIGHT_WINDUP, Kind.LIGHT_PERFORM})
            result.add(new Spec(kind, 0, true));
        for (Kind kind : new Kind[] {Kind.OUT_LIGHT_NOW, Kind.OUT_LIGHT_QUEUED, Kind.SAME_TS})
            for (float meter : new float[] {0, 0.75F, 1}) result.add(new Spec(kind, meter, true));
        result.add(new Spec(Kind.COOLDOWN, 0, false));
        for (float meter : new float[] {0, 0.75F, 1}) {
            result.add(new Spec(Kind.HEAVY_CONTROL, meter, false));
            result.add(new Spec(Kind.HEAVY_CONTROL, meter, true));
        }
        return result;
    }

    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final List<Spec> specs = cases();
        final List<Object> listeners = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        Scene scene;
        Throwable eventFailure;
        int index;
        int checks;
        int nativeLevelTicks;
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "TS punch parity suites overlap");
            this.helper = helper;
            helper.testInfo.addListener(this);
            active = this;
        }

        void identity() {
            for (Class<?> type : new Class<?>[] {TheWorldTSPunchAbility.class, TheWorldTSPunch.class,
                    StandEntityHeavyPunchAbility.class, StandEntityHeavyPunch.class, AbilityInput.class,
                    EntityActionInstance.class, TimeStopLearning.class, TheWorldTSPunchParityGameTests.class}) {
                String actual = sha(type);
                var origin = type.getProtectionDomain().getCodeSource();
                String expected = System.getProperty("rotp.tsPunch.expected." + type.getName());
                helper.assertTrue(origin != null && (expected == null || expected.equalsIgnoreCase(actual)),
                        "TS punch loaded-class receipt " + type.getName());
                log("LOADED class=" + type.getName() + " source=" + origin.getLocation() + " sha256=" + actual);
            }
        }

        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> capture(() -> scene.pre(event.getEntity()));
            Consumer<EntityTickEvent.Post> post = event -> capture(() -> scene.post(event.getEntity()));
            Consumer<LivingIncomingDamageEvent> damage = event -> capture(() -> scene.incoming(event));
            Consumer<LevelTickEvent.Pre> level = event -> {
                if (!closed && event.getLevel() == helper.getLevel()) nativeLevelTicks++;
            };
            listeners.add(pre); listeners.add(post); listeners.add(damage); listeners.add(level);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, damage);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LevelTickEvent.Pre.class, level);
        }

        void capture(Runnable observation) {
            if (closed || scene == null || eventFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) { eventFailure = error; }
        }

        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw new IllegalStateException("TS punch native-event premise failed", eventFailure);
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "TS punch parity failures; see SUMMARY");
                        helper.succeed();
                        return;
                    }
                    scene = new Scene(this, specs.get(index++));
                    scene.prepare();
                }
                scene.tick();
            }
            catch (RuntimeException | Error error) { close(); throw error; }
        }

        void check(boolean passed, String detail) {
            checks++;
            log((passed ? "PASS " : "FAIL ") + scene.spec.id() + " " + detail);
            if (!passed) failures.add(scene.spec.id() + ": " + detail);
        }

        void close() {
            if (closed) return;
            closed = true;
            try {
                for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
                listeners.clear();
                if (scene != null) scene.close();
            }
            finally {
                if (active == this) active = null;
                log("CLEANUP listeners=" + listeners.size() + " released=" + (active == null));
            }
        }

        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final Map<ChunkPos, Boolean> fixtureChunks = new LinkedHashMap<>();
        final ServerLevel level;
        Actor caster;
        Cow target;
        EntityActionInstance previous;
        EntityActionInstance action;
        ActionPhase beforePhase;
        float beforePhaseTick;
        float meterBefore;
        float lastFinisherBefore;
        float healthBefore;
        float staminaAfterBlink;
        float staminaBeforePerform;
        float staminaAtDamage = Float.NaN;
        float recovery;
        float expectedPerform;
        int worldTicks;
        int standNativeTicks;
        int windupFrames;
        int performFrames;
        int recoveryFrames;
        int incomingCalls;
        int firstActionFrame = -1;
        int lastActionFrame = -1;
        int stampFrame = -1;
        int recoveryFrame = -1;
        int stampTransitions;
        boolean started;
        boolean frame;
        boolean outgoingTried;
        boolean stampSeen;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }

        void prepare() {
            premise(near(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get(), 1), "unchanged default M1");
            ChunkPos cell = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(cell.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    cell.getMiddleBlockZ() + 0.5);
            caster = actor(origin);
            if (spec.kind == Kind.OUT_LIGHT_QUEUED) {
                double speed = spec.meter == 1 ? 24 : 16;
                caster.user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(speed);
                StandEntity.updateStandStatAttributes(caster.stand, caster.user);
                premise(near(caster.stand.getAttackSpeed(), speed), "deterministic native queued-light speed");
            }
            premise(caster.power.usesStamina() && caster.power.isStaminaInfinite() == spec.creative(), "finite/Creative actual stamina policy");
            unlock("time_stop");
            unlock("ts_punch");
            caster.power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP,
                    100 - 5.0F, TimeStopLearning.getMaxTrainingPoints(caster.power), caster.power);
            premise(TimeStopLearning.getSavedTimeStopTicks(caster.power) == 100, "default trained100 duration");
            TheWorldTSPunchAbility ts = tsAbility();
            premise(near(ts.getStaminaCost(), 50) && !ts.skipCappedByBlinkImpliedTicks()
                    && ts.trainsTimeStop(), "registered default TS cost/config remains intact");
            if (spec.kind == Kind.RESOLVE) caster.user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 3));
            premise((ResolveModeEffect.getResolveEffectLvl(caster.user) >= 0) == (spec.kind == Kind.RESOLVE), "actual Resolve exemption control");
            if (!spec.empty) target = cow(origin.add(0, 0, 1.5));
            aim();
            caster.stand.setFinisherMeter(spec.meter);
            if (spec.kind == Kind.LOW_AFTER_BLINK) caster.power.setStamina(200);
            else if (spec.kind == Kind.ZERO || spec.kind == Kind.RESOLVE || spec.kind == Kind.CREATIVE) caster.power.setStamina(0);
            else caster.power.setStamina(caster.power.getMaxStamina());
            if (spec.kind == Kind.LOW_AFTER_BLINK) premise(near(caster.power.getStamina(), 200), "real initial200, not post-blink resource injection");
            if (spec.predecessor() || spec.kind == Kind.HEAVY_CONTROL) {
                Press input = press(spec.lightPredecessor() ? "punch" : "heavy_punch", FIRST, false);
                premise(input.admitted && input.success && !input.buffered && input.action != null, "registered predecessor installed");
                previous = input.action;
                String resolved = ((Ability) previous.ability).name();
                premise(spec.lightPredecessor() ? previous instanceof StandEntityPunch
                        : spec.meter < 0.5F ? previous instanceof TheWorldHeavyPunch && resolved.equals("heavy_punch")
                        : previous instanceof TheWorldKick && resolved.equals("kick"),
                        "real registered predecessor, never a constructed phase: " + previous.getClass().getName() + "/" + resolved);
                log("PREDECESSOR " + spec.id() + " resolved=" + resolved);
                if (spec.kind == Kind.HEAVY_CONTROL) {
                    action = previous;
                    started = true;
                    meterBefore = spec.meter;
                    healthBefore = target != null ? target.getHealth() : 0;
                    staminaAfterBlink = caster.power.getStamina();
                    recovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
                    expectedPerform = action.phasesLength.getFloat(ActionPhase.PERFORM);
                    check(near(((StandEntityHeavyPunch) action).finisherValue, spec.meter)
                            && near(caster.stand.getFinisherMeter(), Math.max(spec.meter - 0.51F, 0)),
                            "unchanged registered heavy/finisher capture and single debit on hit and miss");
                }
            }
            else attemptTs();
        }

        void tick() {
            if (done) return;
            premise(++worldTicks < 120, "native scene did not finish " + (action != null ? action.getPhase() : previous != null ? previous.getPhase() : null));
            if (started && action != null && action.isOver()) finish();
        }

        void attemptTs() {
            EntityActionInstance predecessor = caster.stand.getCurStandAction();
            if (spec.reserve()) alignEmptyBlinkDestination();
            meterBefore = caster.stand.getFinisherMeter();
            lastFinisherBefore = caster.stand.getLastHeavyFinisherValue();
            healthBefore = target != null ? target.getHealth() : 0;
            int beforeAge = caster.stand.tickCount;
            int userAge = caster.user.tickCount;
            int overlay = caster.stand.overlayTickCount;
            int beforeNative = standNativeTicks;
            int beforeLevel = suite.nativeLevelTicks;
            long beforeTime = level.getGameTime();
            int affordable = tsAbility().getAffordableTimeSkipTicks(caster.power);
            if (spec.reserve()) premise(affordable >= (predecessor == null ? 10 : 30), "actual affordable reserve before installation");
            Press input = press("ts_punch", TS, true);
            check(input.admitted && input.success && !input.buffered, "TS checked registered CLICK starts immediately, not queued");
            if (!input.success || !(input.action instanceof TheWorldTSPunch)) { done = true; return; }
            action = input.action;
            ensureActionChunk();
            started = true;
            recovery = StandStatFormulas.getHeavyAttackRecovery(caster.stand.getAttackSpeed(), 0);
            expectedPerform = 1;
            staminaAfterBlink = caster.power.getStamina();
            if (spec.kind == Kind.ZERO || spec.kind == Kind.RESOLVE)
                check(near(staminaAfterBlink, 0), "zero starting resource survives the real blink path");
            if (spec.kind == Kind.CREATIVE) check(near(staminaAfterBlink, caster.power.getMaxStamina()),
                    "Creative native stamina getter remains at its exempt maximum");
            check(action.getPowerUser() == caster.user && action.getPerformer() == caster.stand
                    && ((Ability) action.ability).name().equals("ts_punch"), "real registered owner/performer/action identity");
            check(action.getPhase() == ActionPhase.WINDUP && near(action.getPhaseTick(), 0), "unchanged native W5 CLICK starts at zero");
            check(near(caster.stand.getFinisherMeter(), meterBefore)
                    && near(caster.stand.getLastHeavyFinisherValue(), lastFinisherBefore)
                    && near(((StandEntityHeavyPunch) action).finisherValue, 0), "F1 no debit, snapshot or stale-finisher capture");
            check(near(action.phasesLength.getFloat(ActionPhase.RECOVERY), recovery), "F1 configured recovery uses zero finisher");
            check(near(action.phasesLength.getFloat(ActionPhase.PERFORM), 1), "F4 explicit TS P1");
            check(near(action.userWalkSpeed, 1), "unchanged TS walking factor1");
            if (spec.kind == Kind.LOW_AFTER_BLINK) {
                premise(staminaAfterBlink > 0 && staminaAfterBlink < tsAbility().getStaminaCost(),
                        "actual finite post-blink balance below50 at default trained100/initial200: " + staminaAfterBlink);
                premise(caster.stand.tickCount > beforeAge && caster.stand.tickCount - beforeAge <= affordable,
                        "actual affordable native skip, not a copied cost formula");
            }
            if (spec.kind == Kind.PAID) premise(staminaAfterBlink > tsAbility().getStaminaCost(), "genuinely payable strike control");
            if (spec.reserve()) {
                int expected = predecessor == null ? 10 : 30;
                check(caster.stand.tickCount - beforeAge == expected && caster.user.tickCount - userAge == expected
                        && caster.stand.overlayTickCount - overlay == expected, "native zero-distance reserve=" + expected + " oldAction=" + (predecessor != null));
                check(standNativeTicks == beforeNative && suite.nativeLevelTicks == beforeLevel && level.getGameTime() == beforeTime,
                        "skip changes entity ages only; native clocks unchanged");
                if (predecessor != null) check(predecessor == previous && predecessor.isOver(), "original predecessor was replaced, not pre-cleared to lose20");
            }
            log("PRESS " + spec.id() + " budgetBefore=" + affordable + " skipped=" + (caster.stand.tickCount - beforeAge)
                    + " postBlink=" + staminaAfterBlink + " meter=" + meterBefore + " phases=" + action.phasesLength);
        }

        void pre(Entity entity) {
            if (entity != caster.stand || done) return;
            standNativeTicks++;
            if (!started && previous != null && previous.getPhase() == spec.predecessorPhase()) {
                premise(caster.stand.getCurStandAction() == previous && !previous.isOver(), "native live predecessor at " + spec.predecessorPhase());
                if (spec.deniedPhase()) {
                    Press blocked = press("ts_punch", TS, true);
                    premise(blocked.admitted, "main registered admission passed; the live Stand phase must refuse the keypress");
                    check(!blocked.success && !blocked.buffered && caster.stand.getCurStandAction() == previous,
                            "TS cannot start or queue from actual " + spec.predecessorPhase());
                    done = true;
                    return;
                }
                attemptTs();
            }
            if (done || action == null) return;
            EntityActionInstance current = caster.stand.getCurStandAction();
            if (spec.outgoing() && !outgoingTried && current == action && action.getPhase() == ActionPhase.RECOVERY) tryOutgoing();
            current = caster.stand.getCurStandAction();
            if (current != null && current != action && spec.outgoing()) {
                verifyOutgoingReplacement(current);
                done = true;
                return;
            }
            frame = current == action && !action.isOver();
            if (!frame) return;
            if (firstActionFrame < 0) firstActionFrame = standNativeTicks;
            lastActionFrame = standNativeTicks;
            beforePhase = action.getPhase();
            beforePhaseTick = action.getPhaseTick();
            if (beforePhase == ActionPhase.WINDUP) windupFrames++;
            if (beforePhase == ActionPhase.PERFORM) {
                performFrames++;
                if (beforePhaseTick < 1) staminaBeforePerform = caster.power.getStamina();
            }
            if (beforePhase == ActionPhase.RECOVERY) recoveryFrames++;
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (done || action == null || event.getEntity() != target || event.getSource().getDirectEntity() != caster.stand
                    || caster.stand.getCurStandAction() != action) return;
            incomingCalls++;
            staminaAtDamage = caster.power.getStamina();
            check(action.getPhase() == ActionPhase.PERFORM && action.getPhaseTick() < 1 && performFrames == 1,
                    "actual first native PERFORM callback owns the hit");
            check(near(staminaAtDamage, staminaBeforePerform), "F2 strike occurs before configured debit, incoming stamina=" + staminaAtDamage);
            check(near(event.getOriginalAmount(), StandStatFormulas.getHeavyAttackDamage(caster.stand.getAttackDamage())), "unchanged heavy damage independent of meter");
            if (action instanceof TheWorldTSPunch) {
                // 1.16 HeavyPunchInstance: the additional 4 is pushed after the hurt in two halves, never inside it
                check(event.getSource() instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == caster.power
                        && modified.jojo_ripples$standInvulTicks() == 10 && near(modified.jojo_ripples$addKnockback(), 0),
                        "owned TS source, invul10, and its additional knockback kept out of the native hurt");
            }
            log("DAMAGE " + spec.id() + " nativeFrame=" + standNativeTicks + " phaseTick=" + action.getPhaseTick()
                    + " amount=" + event.getOriginalAmount() + " stamina=" + staminaAtDamage + " calls=" + incomingCalls);
        }

        void post(Entity entity) {
            if (entity != caster.stand || done || action == null) return;
            if (frame) {
                if (!stampSeen && action.punchedTarget != null) {
                    stampSeen = true;
                    stampTransitions++;
                    stampFrame = standNativeTicks;
                    float cost = action instanceof TheWorldTSPunch ? tsAbility().getStaminaCost() : 50;
                    float expected = caster.power.isStaminaInfinite() ? staminaBeforePerform : Math.max(staminaBeforePerform - cost, 0);
                    check(near(caster.power.getStamina(), expected), "one post-strike configured debit, before=" + staminaBeforePerform + " after=" + caster.power.getStamina());
                    check(beforePhase == ActionPhase.PERFORM && beforePhaseTick < 1 && performFrames == 1,
                            "native hit/miss bookkeeping produced on the first PERFORM callback");
                    check(action.punchedTarget.getMainEntity() == target, "unchanged actual hit/miss target snapshot");
                    if (action instanceof TheWorldTSPunch) {
                        check(near(caster.stand.getFinisherMeter(), meterBefore), "F1 hit/miss keeps meter through native strike");
                        check(action.getPhase() == ActionPhase.RECOVERY, "F4 strike ends directly in recovery on this native tick");
                    }
                }
                if (recoveryFrame < 0 && action.getPhase() == ActionPhase.RECOVERY) recoveryFrame = standNativeTicks;
                log("FRAME " + spec.id() + " native=" + standNativeTicks + " before=" + beforePhase + ":" + beforePhaseTick
                        + " after=" + action.getPhase() + ":" + action.getPhaseTick() + " W/P/R=" + windupFrames + "/" + performFrames + "/" + recoveryFrames);
            }
            frame = false;
            if (action.isOver() && spec.kind != Kind.OUT_LIGHT_QUEUED) finish();
        }

        void tryOutgoing() {
            outgoingTried = true;
            premise(spec.creative() && !caster.power.isAbilityOnCooldown("ts_punch"), "same-ability predicate isolated from cooldown without editing timers");
            if (spec.kind == Kind.SAME_TS) {
                Press repeated = press("ts_punch", FOLLOW, true);
                premise(repeated.admitted, "Creative main admission passed; same-task rejection must come from actual Stand input");
                check(!repeated.success && !repeated.buffered && caster.stand.getCurStandAction() == action,
                        "same TS recovery click is neither chainable nor queued");
            }
            else {
                Press light = press("punch", FOLLOW, spec.kind == Kind.OUT_LIGHT_QUEUED);
                check(light.admitted && !light.success && light.buffered == (spec.kind == Kind.OUT_LIGHT_QUEUED)
                        && caster.stand.getCurStandAction() == action, "donor basic TS recovery has no immediate light exit; queue=" + light.buffered);
            }
        }

        void verifyOutgoingReplacement(EntityActionInstance current) {
            check(spec.kind == Kind.OUT_LIGHT_QUEUED && outgoingTried && current instanceof StandEntityPunch
                    && current.getPowerUser() == caster.user && current.getPerformer() == caster.stand,
                    "only a genuinely queued light replaces the TS action");
            check(recoveryFrames == (int) recovery && windupFrames == 5 && performFrames == 1,
                    "queued light waits for the full native TS lifetime, not a finisher recovery exit");
            if (spec.kind == Kind.OUT_LIGHT_QUEUED && current != null) {
                float windup = current.phasesLength.getFloat(ActionPhase.WINDUP);
                check(near(windup, spec.meter == 1 ? 0 : 1), "native zero/nonzero light windup control=" + windup);
                boolean initialPhase = current.getPhase() == ActionPhase.WINDUP
                        || windup == 0 && current.getPhase() == ActionPhase.PERFORM;
                check(initialPhase && near(current.getPhaseTick(), 0) && near(current.getFullTicksPassed(), 0),
                        "native queue replay has zero elapsed skip; W=" + windup + " phase=" + current.getPhase()
                                + " tick=" + current.getPhaseTick() + " full=" + current.getFullTicksPassed()
                                + " speed=" + caster.stand.getAttackSpeed() + " counter=" + caster.stand.guardCounter());
            }
            verifyLifetime();
        }

        void finish() {
            if (done) return;
            if (spec.kind == Kind.OUT_LIGHT_QUEUED) {
                EntityActionInstance current = caster.stand.getCurStandAction();
                if (current == null) return;
                verifyOutgoingReplacement(current);
                done = true;
                return;
            }
            verifyLifetime();
            if (spec.kind == Kind.COOLDOWN) {
                premise(caster.stand.getCurStandAction() == null && caster.power.isAbilityOnCooldown("ts_punch"), "natural idle end still inside real50 cooldown");
                Press cooled = press("ts_punch", FOLLOW, true);
                check(!cooled.admitted && !cooled.success && !cooled.buffered && caster.stand.getCurStandAction() == null,
                        "native cooldown admission remains intact without a current task");
            }
            done = true;
        }

        void verifyLifetime() {
            // 1.16 StandEntityTask.moveToPhase: the ordinary heavy's RECOVERY is the length set at RECOVERY entry
            if (spec.kind == Kind.HEAVY_CONTROL) recovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
            check(stampSeen && stampTransitions == 1 && incomingCalls == (spec.empty ? 0 : 1), "one actual callback/attempt and hit-or-miss count=" + incomingCalls);
            check(performFrames == (int) Math.ceil(expectedPerform) && recoveryFrames == (int) recovery,
                    "native PERFORM/RECOVERY counts=" + performFrames + "/" + recoveryFrames);
            int expectedWindup = (int) Math.ceil(action.phasesLength.getFloat(ActionPhase.WINDUP));
            check(windupFrames == expectedWindup && lastActionFrame - firstActionFrame + 1 == expectedWindup + (int) Math.ceil(expectedPerform) + (int) recovery,
                    "full native task lifetime=" + (lastActionFrame - firstActionFrame + 1));
            if (action instanceof TheWorldTSPunch) {
                check(stampFrame == recoveryFrame && windupFrames == 5 && performFrames == 1,
                        "F4 native W5/P1 boundary and same-tick recovery");
                check(level.getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(caster.user.getId()).isEmpty(),
                        "blink does not install or advance an owned real time stop");
            }
            if (target != null) check(target.getHealth() < healthBefore, "native admitted target actually lost health");
            if (spec.outgoing()) check(outgoingTried, "native outgoing Recovery control reached");
        }

        void ensureActionChunk() {
            ChunkPos chunk = caster.stand.chunkPosition();
            fixtureChunks.computeIfAbsent(chunk, key -> {
                boolean alreadyForced = level.getForcedChunks().contains(key.toLong());
                if (!alreadyForced) level.setChunkForced(key.x, key.z, true);
                level.getChunk(key.x, key.z);
                return alreadyForced;
            });
        }

        Press press(String name, short key, boolean canBuffer) {
            Ability base = caster.power.getAbility(name);
            premise(base != null, "registered ability exists " + name);
            var selected = caster.power.updateAvailableMoves().getContextVariationContainer(base);
            boolean admitted = selected != null && AbilityInput.withConditionCheck(selected, caster.user, InputMethod.CLICK);
            if (!admitted) return new Press(false, false, false, caster.stand.getCurStandAction());
            BufferingState buffer = canBuffer ? BufferingState.clickCanBuffer() : BufferingState.clickOnly();
            var input = AbilityInput.keyPress(key, selected.ability, caster.user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
            EntityActionInstance current = input != null && input.action instanceof EntityActionInstance a ? a : caster.stand.getCurStandAction();
            return new Press(true, buffer.isActionSuccess, buffer.shouldBuffer, current);
        }

        Actor actor(Vec3 position) {
            Player user = GameTestPlayers.makeServerMockPlayer(suite.helper, spec.creative() ? GameType.CREATIVE : GameType.SURVIVAL);
            caster = new Actor(user, null, null, null);
            user.getAbilities().instabuild = spec.creative();
            user.getAbilities().invulnerable = false;
            user.setNoGravity(true);
            user.noPhysics = true;
            user.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(user), "actual server player added");
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
            caster = new Actor(user, power, type, null);
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "public transition grants registered TW");
            power.setResolveLevel(3);
            premise(type.summon(user, power), "native full TW summon");
            StandEntity stand = power.getSummonedStandEntity();
            caster = new Actor(user, power, type, stand);
            premise(stand != null && stand.getUser() == user && !stand.isArmsOnlyMode(), "actual full body/owner binding");
            stand.summonLockTicks = 0;
            stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false);
            stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, 0, 0);
            stand.setYHeadRot(0);
            stand.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return caster;
        }

        Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(level);
            premise(cow != null, "real Cow created");
            target = cow;
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.noPhysics = true;
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            cow.setHealth(200);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(cow), "native damage target added");
            return cow;
        }

        void aim() {
            ActionTarget aim = target != null ? new ActionTarget(target) : ActionTarget.EMPTY;
            LivingComponentAction.getComponent(caster.user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(aim);
            Vec3 point = target != null ? target.getEyePosition() : caster.user.getEyePosition().add(0, 0, 20);
            caster.user.lookAt(EntityAnchorArgument.Anchor.EYES, point);
            caster.user.setYHeadRot(caster.user.getYRot());
            caster.stand.lookAt(EntityAnchorArgument.Anchor.EYES, point);
            caster.stand.setYHeadRot(caster.stand.getYRot());
        }

        void alignEmptyBlinkDestination() {
            premise(target == null, "reserve is tested with an empty unobstructed aim");
            Vec3 position = caster.stand.position().subtract(0, 0, caster.stand.getMaxRange());
            caster.user.moveTo(position.x, position.y, position.z, 0, 0);
            caster.user.setYHeadRot(0);
            caster.stand.setYRot(0);
            caster.stand.setXRot(0);
            caster.stand.setYHeadRot(0);
            LivingComponentAction.getComponent(caster.user).entityAim.setTarget(ActionTarget.EMPTY);
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(ActionTarget.EMPTY);
        }

        TheWorldTSPunchAbility tsAbility() {
            Ability ability = caster.power.getMoveset().getAbility("ts_punch");
            premise(ability instanceof TheWorldTSPunchAbility, "registered TS type");
            return (TheWorldTSPunchAbility) ability;
        }

        void unlock(String skill) {
            var result = UNLOCKS.forceUnlock(caster.power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public unlock " + skill);
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "TS-PUNCH PREMISE " + spec.id() + " " + detail); }
        void check(boolean passed, String detail) { suite.check(passed, detail); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                if (caster != null) {
                    AbilityInput.keyRelease(FIRST, caster.user);
                    AbilityInput.keyRelease(TS, caster.user);
                    AbilityInput.keyRelease(FOLLOW, caster.user);
                    if (caster.stand != null) LivingComponentAction.getComponent(caster.stand).setAction(null, SyncType.NO_SYNC);
                    if (caster.type != null && caster.power != null && caster.power.isSummoned()) caster.type.forceUnsummon(caster.user, caster.power);
                }
            }
            finally {
                if (caster != null) {
                    if (caster.stand != null && !caster.stand.isRemoved()) caster.stand.discard();
                    caster.user.discard();
                }
                if (target != null) target.discard();
                fixtureChunks.forEach((chunk, wasForced) -> {
                    if (!wasForced) level.setChunkForced(chunk.x, chunk.z, false);
                });
                fixtureChunks.clear();
                log("SCENE-CLEANUP " + spec.id() + " actorsRemoved=" + ((caster == null || caster.user.isRemoved()
                        && (caster.stand == null || caster.stand.isRemoved())) && (target == null || target.isRemoved())));
            }
        }
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.001; }

    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing native class " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("TS punch class identity", error); }
    }

    private static void log(String text) { JojoMod.getLogger().info("D-TW-TS-PUNCH " + text); }
}

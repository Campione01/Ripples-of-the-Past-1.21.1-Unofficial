package rotp.core.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands.StandInitStarPlatinum;
import rotp.core.impl.stands.StandInitTheWorld;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.impl.stands.starplatinum.HeavyPunchUppercutAbility.Uppercut;
import rotp.core.impl.stands.theworld.TheWorldHeavyPunchAbility.TheWorldHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.impl.stands.theworld.TheWorldTSPunchAbility.TheWorldTSPunch;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyOriginalPhaseGameTests {
    private static final short ATTACK = 141;
    private static final short FOLLOW = 142;
    private static final short GRAB = 143;
    private static final String BATCH = "heavy_original_phase";
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(HeavyOriginalPhaseGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
    private static Suite active;

    private HeavyOriginalPhaseGameTests() {}

    private enum Family {
        SP_HEAVY("star_platinum"), TW_HEAVY("the_world"),
        UPPERCUT("star_platinum"), KICK("the_world"),
        GRAB_HEAVY("star_platinum"), SHARED_HEAVY("crazy_diamond"),
        CHARGED("star_platinum"), TS_PUNCH("the_world");

        final String stand;
        Family(String stand) { this.stand = stand; }
        boolean original() { return ordinal() <= KICK.ordinal(); }
        boolean finisher() { return this == UPPERCUT || this == KICK; }
    }

    private enum Follow { NONE, EARLY_CLICK, BUFFERED_CLICK }
    private record Spec(Family family, boolean empty, Follow follow, float clickDelay) {
        String id() { return family + (empty ? "-EMPTY" : "-HIT") + "-" + follow + "-delay" + clickDelay; }
    }

    @GameTest(template = "empty", timeoutTicks = 2200, batch = BATCH)
    public static void originalHeavyPerformEndsOnItsImpactTick(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            suite.identity();
            suite.listen();
            helper.onEachTick(suite::tick);
        }
        catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    @AfterBatch(batch = BATCH)
    public static void releaseHeavyOriginalPhaseBatch(ServerLevel level) {
        if (active != null) active.close();
        log("BATCH-CLEANUP released=" + (active == null));
    }

    private static List<Spec> cases() {
        List<Spec> result = new ArrayList<>();
        for (Family family : new Family[] {Family.SP_HEAVY, Family.TW_HEAVY, Family.UPPERCUT, Family.KICK}) {
            result.add(new Spec(family, false, Follow.NONE, 0));
            result.add(new Spec(family, true, Follow.NONE, 0));
        }
        for (Family family : new Family[] {Family.UPPERCUT, Family.KICK}) {
            result.add(new Spec(family, false, Follow.EARLY_CLICK, 0));
            result.add(new Spec(family, false, Follow.BUFFERED_CLICK, 0));
        }
        for (Family family : new Family[] {Family.SP_HEAVY, Family.TW_HEAVY})
            result.add(new Spec(family, false, Follow.NONE, 4));
        for (Family family : new Family[] {Family.GRAB_HEAVY, Family.SHARED_HEAVY, Family.CHARGED, Family.TS_PUNCH})
            result.add(new Spec(family, false, Follow.NONE, 0));
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
        boolean closed;

        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "HEAVY-PHASE overlapping suites");
            this.helper = helper;
            helper.testInfo.addListener(this);
            active = this;
        }

        void identity() {
            for (Class<?> type : new Class<?>[] {StandInitStarPlatinum.class, StandInitTheWorld.class,
                    StandEntityHeavyPunchAbility.class, EntityActionInstance.class, HeavyOriginalPhaseGameTests.class}) {
                String actual = sha(type);
                String expected = System.getProperty("rotp.heavyOriginal.expected." + type.getName());
                helper.assertTrue(expected == null || actual.equalsIgnoreCase(expected), "HEAVY-PHASE class receipt " + type.getName());
                log("LOADED class=" + type.getName() + " sha256=" + actual + " source=" + type.getProtectionDomain().getCodeSource());
            }
        }

        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> capture(() -> scene.pre(event.getEntity()));
            Consumer<EntityTickEvent.Post> post = event -> capture(() -> scene.post(event.getEntity()));
            Consumer<LivingIncomingDamageEvent> incoming = event -> capture(() -> scene.incoming(event));
            listeners.add(pre); listeners.add(post); listeners.add(incoming);
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, incoming);
        }

        void capture(Runnable observation) {
            if (closed || scene == null || eventFailure != null) return;
            try { observation.run(); }
            catch (RuntimeException | Error error) { eventFailure = error; }
        }

        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw new IllegalStateException("HEAVY-PHASE event premise failed", eventFailure);
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == specs.size()) {
                        log("SUMMARY scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close();
                        helper.assertTrue(failures.isEmpty(), "HEAVY-PHASE behavior failures; see SUMMARY");
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

    private record Actor(Player user, StandPower power, StandType type, StandEntity stand) {}
    private record Press(EntityActionInstance action, boolean success, boolean buffered) {}

    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Spec spec;
        final ServerLevel level;
        Actor caster;
        Cow target;
        EntityActionInstance action;
        EntityActionInstance grabAction;
        ActionPhase beforePhase;
        float beforePhaseTick;
        float meterAtPress;
        float staminaAtPress;
        float staminaAfterPress;
        float expectedWindup;
        float expectedRecovery;
        float expectedPerform;
        float expectedSkip;
        int worldTicks;
        int windupCallbacks;
        int performCallbacks;
        int recoveryCallbacks;
        int incomingCalls;
        int stampAge = -1;
        int recoveryAge = -1;
        int impactAge = -1;
        int impactPreCalls = -1;
        float impactPhaseTick = Float.NaN;
        boolean hasFrame;
        boolean stampSeen;
        boolean chargeReleased;
        boolean earlyAttempted;
        boolean followInstalled;
        boolean initialQueueChecked;
        boolean replayStartSeen;
        ActionPhase replayStartPhase;
        float replayStartTick;
        boolean done;
        boolean closed;

        Scene(Suite suite, Spec spec) { this.suite = suite; this.spec = spec; level = suite.helper.getLevel(); }

        void prepare() {
            premise(near(JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get(), 1), "isolated M1 config required");
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8,
                    chunk.getMiddleBlockZ() + 0.5);
            caster = actor(spec.family.stand, origin);
            if (!spec.empty) target = cow(origin.add(0, 0, 1.7));
            aim(target != null ? new ActionTarget(target) : ActionTarget.EMPTY, origin.add(0, 1, 8));
            if (spec.family.finisher()) unlock(spec.family == Family.UPPERCUT ? "uppercut" : "kick");
            if (spec.family == Family.TS_PUNCH) { unlock("time_stop"); unlock("ts_punch"); }
            if (spec.family == Family.GRAB_HEAVY) {
                unlock("grab_throw");
                grabAction = press("grab", InputMethod.CLICK, GRAB, 0, false).action;
                premise(grabAction != null, "registered native grab started");
            }
            else beginAttack();
        }

        void tick() {
            premise(++worldTicks < 140, "natural action did not finish, phase=" + (action != null ? action.getPhase() : null));
            if (action == null) {
                if (LivingComponentGrab.getEntityGrabbedBy(caster.stand) == target) beginAttack();
                return;
            }
            if (spec.family == Family.CHARGED && !chargeReleased && action.getPhase() == ActionPhase.WINDUP) {
                chargeReleased = true;
                AbilityInput.keyRelease(ATTACK, caster.user);
            }
            if (spec.follow == Follow.BUFFERED_CLICK && caster.stand.getCurStandAction() != action) verifyBufferedReplacement();
        }

        void beginAttack() {
            caster.stand.setFinisherMeter(spec.family.finisher() ? 0.75F : 0);
            meterAtPress = caster.stand.getFinisherMeter();
            staminaAtPress = caster.power.getStamina();
            premise(caster.power.usesStamina() && !caster.power.isStaminaInfinite(), "finite native stamina required");
            float speed = (float) caster.stand.getAttackSpeed();
            expectedWindup = heavyWindup(speed, meterAtPress);
            expectedRecovery = heavyRecovery(speed, meterAtPress);
            expectedPerform = spec.family.original() || spec.family == Family.TS_PUNCH ? 1 : 6;
            String name = spec.family == Family.CHARGED ? "heavy_charged" : spec.family == Family.TS_PUNCH ? "ts_punch" : "heavy_punch";
            InputMethod method = spec.family == Family.CHARGED ? InputMethod.HOLD : InputMethod.CLICK;
            Press input = press(name, method, ATTACK, spec.clickDelay, false);
            premise(input.success && input.action != null, "registered input installed the attack");
            action = input.action;
            staminaAfterPress = caster.power.getStamina();
            // TS admission pays its native blink before the later punch debit.
            float startCost = spec.family == Family.TS_PUNCH ? 225 * 0.8F * 0.5F + 5 * (9 * 100F / 5 * 0.8F) : 0;
            check(near(staminaAtPress - staminaAfterPress, startCost), "native action-set cost=" + (staminaAtPress - staminaAfterPress));
            String selected = ((Ability) action.ability).name();
            String expectedName = switch (spec.family) {
                case UPPERCUT -> "finisher_uppercut";
                case KICK -> "kick";
                case GRAB_HEAVY -> "grab_heavy_punch";
                case CHARGED -> "heavy_charged";
                case TS_PUNCH -> "ts_punch";
                default -> "heavy_punch";
            };
            premise(selected.equals(expectedName) && correctClass(), "actual registered class/name selected: " + selected + " / " + action.getClass().getName());
            if (spec.family == Family.CHARGED) {
                expectedPerform = expectedWindup * 0.5F;
                expectedRecovery = 12;
                check(near(action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE), expectedWindup * 1.5F), "unchanged charged button timer");
            }
            else {
                if (spec.family == Family.TS_PUNCH) expectedWindup = 5;
                if (spec.family == Family.TS_PUNCH) {
                    expectedRecovery = heavyRecovery(speed, 0);
                    check(action instanceof StandEntityHeavyPunch heavy && near(heavy.finisherValue, 0)
                            && near(caster.stand.getFinisherMeter(), meterAtPress), "TS punch keeps the meter and uses zero finisher recovery");
                }
                else check(action instanceof StandEntityHeavyPunch heavy && near(heavy.finisherValue, meterAtPress)
                        && near(caster.stand.getFinisherMeter(), Math.max(meterAtPress - 0.51F, 0)), "native finisher snapshot and single0.51 debit");
            }
            expectedSkip = Math.min(spec.clickDelay, Math.max(expectedWindup - 1, 0));
            if (spec.family != Family.CHARGED) {
                check(near(action.phasesLength.getFloat(ActionPhase.WINDUP), expectedWindup), "stored donor windup=" + action.phasesLength.getFloat(ActionPhase.WINDUP));
                check(action.getPhase() == ActionPhase.WINDUP && near(action.getPhaseTick(), expectedSkip), "CLICK resolution skips windup only, initialTick=" + action.getPhaseTick());
            }
            check(near(action.phasesLength.getFloat(ActionPhase.RECOVERY), expectedRecovery), "stored recovery=" + action.phasesLength.getFloat(ActionPhase.RECOVERY));
            check(near(action.phasesLength.getFloat(ActionPhase.PERFORM), expectedPerform), "stored PERFORM=" + action.phasesLength.getFloat(ActionPhase.PERFORM) + " expected=" + expectedPerform);
            check(near(action.userWalkSpeed, walkDuringAttack()), "native start movement factor=" + action.userWalkSpeed);
            if (spec.family == Family.GRAB_HEAVY) check(action.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB, "port-only grab context remains independent");
            log("PRESS " + spec.id() + " selected=" + selected + " speed=" + speed + " snapshot=" + meterAtPress + " phases=" + action.phasesLength);
            if (spec.follow == Follow.BUFFERED_CLICK) {
                Press queued = press("punch", InputMethod.CLICK, FOLLOW, 4, true);
                check(queued.buffered && !queued.success && caster.stand.getCurStandAction() == action,
                        "native clickCanBuffer queues without replacing WINDUP");
                initialQueueChecked = true;
            }
        }

        boolean correctClass() {
            return switch (spec.family) {
                case SP_HEAVY, GRAB_HEAVY -> action.getClass() == StandEntityHeavyPunch.class;
                case TW_HEAVY -> action.getClass() == TheWorldHeavyPunch.class;
                case UPPERCUT -> action.getClass() == Uppercut.class;
                case KICK -> action.getClass() == TheWorldKick.class;
                case CHARGED -> action.getClass() == StandEntityChargedHeavy.class;
                case TS_PUNCH -> action.getClass() == TheWorldTSPunch.class;
                case SHARED_HEAVY -> action instanceof StandEntityHeavyPunch;
            };
        }

        void pre(Entity entity) {
            if (entity != caster.stand || action == null || done) return;
            EntityActionInstance current = caster.stand.getCurStandAction();
            if (spec.follow == Follow.BUFFERED_CLICK && current instanceof StandEntityPunch && current != action && !replayStartSeen) {
                replayStartSeen = true;
                replayStartPhase = current.getPhase();
                replayStartTick = current.getPhaseTick();
            }
            hasFrame = current == action && !action.isOver();
            if (!hasFrame) return;
            beforePhase = action.getPhase();
            beforePhaseTick = action.getPhaseTick();
            if (beforePhase == ActionPhase.WINDUP && spec.family != Family.CHARGED) windupCallbacks++;
            if (beforePhase == ActionPhase.PERFORM) performCallbacks++;
            if (beforePhase == ActionPhase.RECOVERY) recoveryCallbacks++;
        }

        void incoming(LivingIncomingDamageEvent event) {
            if (action == null || event.getEntity() != target || event.getSource().getDirectEntity() != caster.stand
                    || caster.stand.getCurStandAction() != action) return;
            incomingCalls++;
            if (incomingCalls == 1) {
                impactAge = caster.stand.tickCount;
                impactPhaseTick = action.getPhaseTick();
                impactPreCalls = performCallbacks;
                check(action.getPhase() == ActionPhase.PERFORM, "real damage belongs to PERFORM");
                check(event.getSource() instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() == caster.power,
                        "real damage source retains owning power");
            }
            log("IMPACT " + spec.id() + " age=" + caster.stand.tickCount + " phaseTick=" + action.getPhaseTick()
                    + " amount=" + event.getOriginalAmount() + " calls=" + incomingCalls);
        }

        void post(Entity entity) {
            if (entity != caster.stand || action == null || done) return;
            if (hasFrame) {
                log("FRAME " + spec.id() + " age=" + caster.stand.tickCount + " pre=" + beforePhase + ":" + beforePhaseTick
                        + " post=" + action.getPhase() + ":" + action.getPhaseTick() + " Pcallbacks=" + performCallbacks);
                if (!stampSeen && action.punchedTarget != null) observeStamp();
                if (action.getPhase() == ActionPhase.RECOVERY && recoveryAge < 0) observeRecovery();
                if (beforePhase == ActionPhase.PERFORM && action.getPhase() == ActionPhase.PERFORM)
                    check(near(action.userWalkSpeed, walkDuringAttack()), "non-recovery movement factor preserved");
                if (beforePhase == ActionPhase.RECOVERY)
                    check(near(action.userWalkSpeed, 1), "recovery movement factor1");
            }
            if (stampSeen && spec.follow == Follow.EARLY_CLICK && !earlyAttempted) {
                earlyAttempted = true;
                Press follow = press("punch", InputMethod.CLICK, FOLLOW, 0, false);
                followInstalled = follow.success && replacementIsLight();
                check(followInstalled, "native unbuffered finisher follow-up at first post-impact opportunity");
                if (followInstalled) finishReplacement();
            }
            if (!done && spec.follow == Follow.EARLY_CLICK && earlyAttempted && !followInstalled && recoveryAge >= 0) {
                Press retry = press("punch", InputMethod.CLICK, FOLLOW, 0, false);
                followInstalled = retry.success && replacementIsLight();
                check(followInstalled, "unchanged finisher follow-up succeeds at actual RECOVERY");
                if (followInstalled) finishReplacement();
            }
            if (!done && spec.follow == Follow.BUFFERED_CLICK && caster.stand.getCurStandAction() != action) verifyBufferedReplacement();
            if (!done && spec.follow == Follow.NONE && action.isOver()) {
                check(stampSeen && recoveryAge >= 0, "native action reached perform and recovery before completion");
                check(performCallbacks == (int) Math.ceil(expectedPerform), "actual PERFORM callbacks=" + performCallbacks);
                check(recoveryCallbacks == (int) expectedRecovery, "natural recovery callbacks=" + recoveryCallbacks);
                verifyImpactCount();
                done = true;
            }
            hasFrame = false;
        }

        void observeStamp() {
            stampSeen = true;
            stampAge = caster.stand.tickCount;
            check(action.punchedTarget.getMainEntity() == target, "native performed target including EMPTY");
            float cost = spec.family == Family.CHARGED ? 100 : 50;
            check(near(staminaAfterPress - caster.power.getStamina(), cost), "single native perform cost=" + (staminaAfterPress - caster.power.getStamina()));
            if (spec.family != Family.CHARGED) {
                check(performCallbacks == 1 && (!spec.empty ? impactAge == stampAge && impactPreCalls == 1 && impactPhaseTick < 1 : incomingCalls == 0),
                        "one first-PERFORM callback performs actual hit/empty attempt");
                check(windupCallbacks == (int) Math.ceil(expectedWindup - expectedSkip), "natural windup callbacks=" + windupCallbacks + " skip=" + expectedSkip);
            }
            if (spec.family.original()) check(action.getPhase() == ActionPhase.RECOVERY,
                    "RECOVERY begins at the end of the impact/empty-perform tick, post=" + action.getPhase());
            log("STAMP " + spec.id() + " age=" + stampAge + " cost=" + cost + " post=" + action.getPhase());
        }

        void observeRecovery() {
            recoveryAge = caster.stand.tickCount;
            check(performCallbacks == (int) Math.ceil(expectedPerform), "PERFORM-to-RECOVERY natural callback count=" + performCallbacks);
            if (spec.family.original()) check(stampSeen && recoveryAge == stampAge,
                    "same performer tick stamp=" + stampAge + " recovery=" + recoveryAge);
            check(near(action.userWalkSpeed, 1), "full movement returns on real RECOVERY boundary");
            if (spec.follow == Follow.NONE && (spec.family == Family.SP_HEAVY || spec.family == Family.TW_HEAVY)) {
                Press blocked = press("punch", InputMethod.CLICK, FOLLOW, 0, false);
                check(!blocked.success && !blocked.buffered && caster.stand.getCurStandAction() == action,
                        "ordinary heavy recovery still refuses unbuffered replacement");
            }
        }

        void verifyBufferedReplacement() {
            check(initialQueueChecked && recoveryAge >= 0 && replacementIsLight(), "native queued click replays only after actual RECOVERY");
            if (replayStartSeen) check(replayStartPhase == ActionPhase.WINDUP && near(replayStartTick, 0),
                    "natural Pre bracket: buffered CLICK replay retains zero windup skip");
            log("BUFFER-REPLAY " + spec.id() + " preObserved=" + replayStartSeen + " phase=" + replayStartPhase + " tick=" + replayStartTick);
            finishReplacement();
        }

        boolean replacementIsLight() {
            EntityActionInstance current = caster.stand.getCurStandAction();
            return current instanceof StandEntityPunch && current != action
                    && current.getPowerUser() == caster.user && current.getPerformer() == caster.stand;
        }

        void finishReplacement() {
            check(performCallbacks == 1 && recoveryAge == stampAge, "finisher replacement follows the one-callback original boundary");
            verifyImpactCount();
            done = true;
        }

        void verifyImpactCount() { check(incomingCalls == (spec.empty ? 0 : 1), "actual primary damage callbacks=" + incomingCalls); }
        float walkDuringAttack() { return spec.family == Family.TS_PUNCH || spec.family == Family.CHARGED ? 1 : 0.5F; }

        Press press(String name, InputMethod method, short key, float delay, boolean canBuffer) {
            Ability base = caster.power.getAbility(name);
            premise(base != null, "registered ability exists " + name);
            var selected = caster.power.updateAvailableMoves().getContextVariationContainer(base);
            premise(selected != null && AbilityInput.withConditionCheck(selected, caster.user, method), "public condition/context admission " + name);
            BufferingState buffer = canBuffer ? BufferingState.clickCanBuffer() : BufferingState.clickOnly();
            var input = AbilityInput.keyPress(key, selected.ability, caster.user, null, method, delay, buffer, base.getAbilityId());
            EntityActionInstance installed = input != null && input.action instanceof EntityActionInstance a ? a : caster.stand.getCurStandAction();
            return new Press(installed, buffer.isActionSuccess, buffer.shouldBuffer);
        }

        Actor actor(String standName, Vec3 position) {
            Player user = GameTestPlayers.makeServerMockPlayer(suite.helper, GameType.SURVIVAL);
            user.getAbilities().invulnerable = false;
            user.setNoGravity(true); user.noPhysics = true;
            user.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(user), "real player actor added");
            StandPower power = PowerClass.STAND.attachGet(user);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standName));
            caster = new Actor(user, power, type, null);
            premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
                    "grant registered " + standName);
            power.setResolveLevel(spec.family == Family.TS_PUNCH ? 3 : 2);
            premise(type.summon(user, power), "native full summon");
            StandEntity stand = power.getSummonedStandEntity();
            caster = new Actor(user, power, type, stand);
            premise(stand != null && stand.getUser() == user, "real owner/body binding");
            stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true);
            stand.moveTo(position.x, position.y, position.z, 0, 0); stand.setYHeadRot(0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user);
            power.setStamina(power.getMaxStamina()); stand.updateStrengthMultipliers();
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            return caster;
        }

        Cow cow(Vec3 position) {
            Cow cow = EntityType.COW.create(level);
            premise(cow != null, "real Cow created");
            target = cow;
            cow.setNoAi(true); cow.setNoGravity(true); cow.noPhysics = true;
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); cow.setHealth(200);
            cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            cow.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(cow), "real hurt target added");
            return cow;
        }

        void aim(ActionTarget aim, Vec3 emptyPoint) {
            LivingComponentAction.getComponent(caster.user).entityAim.setTarget(aim);
            LivingComponentAction.getComponent(caster.stand).entityAim.setTarget(aim);
            Vec3 point = target != null ? aim.getCenterPos() : emptyPoint;
            caster.user.lookAt(EntityAnchorArgument.Anchor.EYES, point);
            caster.user.setYHeadRot(caster.user.getYRot());
            caster.stand.lookAt(EntityAnchorArgument.Anchor.EYES, point);
            caster.stand.setYHeadRot(caster.stand.getYRot());
        }

        void unlock(String skill) {
            var result = UNLOCKS.forceUnlock(caster.power, skill);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public skill unlock " + skill);
        }

        void premise(boolean passed, String detail) { suite.helper.assertTrue(passed, "HEAVY-PHASE PREMISE " + spec.id() + " " + detail); }
        void check(boolean passed, String detail) { suite.check(passed, detail); }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                if (caster != null) {
                    AbilityInput.keyRelease(ATTACK, caster.user);
                    AbilityInput.keyRelease(FOLLOW, caster.user);
                    AbilityInput.keyRelease(GRAB, caster.user);
                    if (caster.stand != null) {
                        caster.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
                        LivingComponentAction.getComponent(caster.stand).setAction(null, SyncType.NO_SYNC);
                    }
                    if (caster.type != null && caster.power.isSummoned()) caster.type.forceUnsummon(caster.user, caster.power);
                }
            }
            finally {
                if (caster != null) {
                    if (caster.stand != null && !caster.stand.isRemoved()) caster.stand.discard();
                    caster.user.discard();
                }
                if (target != null) target.discard();
                log("SCENE-CLEANUP " + spec.id() + " actorsRemoved=" + ((caster == null || caster.user.isRemoved()
                        && (caster.stand == null || caster.stand.isRemoved())) && (target == null || target.isRemoved())));
            }
        }
    }

    private static float heavyWindup(float speed, float meter) {
        float base = 40 - speed * 1.25F;
        return (float) Math.ceil(base * 2 / 3 + meter * (base / 3 - base * 2 / 3));
    }

    private static float heavyRecovery(float speed, float meter) {
        float max = (40 - speed * 1.25F) * 0.75F;
        return (float) Math.floor(max + meter * (max / 2 - max));
    }

    private static boolean near(double actual, double expected) { return Math.abs(actual - expected) < 0.001; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IllegalStateException("Missing class resource " + type.getName());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
        catch (Exception error) { throw new IllegalStateException("HEAVY-PHASE identity", error); }
    }
    private static void log(String text) { JojoMod.getLogger().info("D-MELEE-04 " + text); }
}

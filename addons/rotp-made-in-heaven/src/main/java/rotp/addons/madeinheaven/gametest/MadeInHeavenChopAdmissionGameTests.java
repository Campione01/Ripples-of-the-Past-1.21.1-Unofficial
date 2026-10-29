package rotp.addons.madeinheaven.gametest;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.madeinheaven.MadeInHeavenAddon;
import rotp.addons.madeinheaven.ability.MadeInHeavenChopAbility;
import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.target.ActionTarget;

/** The real registered Chop entry, with finite-resource and natural-windup observations. */
@GameTestHolder("rotp_mih_chop_admission")
@PrefixGameTestTemplate(false)
public final class MadeInHeavenChopAdmissionGameTests {
    private static final short INPUT = 145;
    private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
            MadeInHeavenAddon.id("chop_admission_tests"));
    private static Suite active;
    private enum Case {
        LOW_49_99, AT_50, WINDUP_SHORTAGE, ZERO_SPEED, ZERO_ENTITY_REACH, ZERO_BLOCK_REACH,
        COOLDOWN, DEAD_STAND, STUN_STAND, MISSING_ARMS, GRAB_CONTEXT, CHECKED_CHOP_GRABBED
    }
    private MadeInHeavenChopAdmissionGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mih_chop_admission", timeoutTicks = 1600,
            batch = "mihChopAdmission03")
    public static void chopChecksInitialFiniteStaminaWithoutMeleeStats(GameTestHelper helper) {
        Suite suite = new Suite(helper);
        try {
            for (Class<?> type : new Class<?>[] {MadeInHeavenAddon.class, MadeInHeavenChopAbility.class,
                    StandEntity.class, AbilityInput.class, MadeInHeavenChopAdmissionGameTests.class}) {
                String actual = sha(type), expected = System.getProperty("mih.chop.expected." + type.getName());
                helper.assertTrue(expected == null || expected.equalsIgnoreCase(actual), "Native class mismatch " + type.getName());
                log("IDENTITY " + type.getName() + "=" + actual);
            }
            suite.listen(); helper.onEachTick(suite::tick);
        } catch (RuntimeException | Error failure) { suite.close(); throw failure; }
    }

    @AfterBatch(batch = "mihChopAdmission03")
    public static void releaseAdmissionBatch(ServerLevel level) {
        if (active != null) active.close(); log("BATCH-CLEANUP active=" + (active != null));
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mih_chop_admission", timeoutTicks = 400,
            batch = "mihChopFinisherSlot")
    public static void heavyInputStaysChopWhileChopCoolsDownOrRuns(GameTestHelper helper) {
        SlotProbe probe = new SlotProbe(helper);
        helper.testInfo.addListener(probe);
        helper.onEachTick(probe::tick);
    }

    /**
     * 1.16 StandEntityHeavyAttack.getFinisherVariationIfPresent: a full meter or a running Chop made the heavy input
     * Chop, which its own cooldown or the running Chop (canClickDuringTask) then refused with a plain NEGATIVE.
     * Each step runs on its own tick, as the server caches a power's usable abilities for the tick.
     */
    private static final class SlotProbe implements GameTestListener {
        private static final short HEAVY = 146, LIGHT = 147;
        private enum Step { SETUP, COOLDOWN, PRESS_CHOP, WINDUP, RECOVERY, DONE }
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        Step step = Step.SETUP;
        Player user;
        StandPower power;
        StandEntity stand;
        Ability heavy;
        Ability chop;
        EntityActionInstance running;
        int ticks;
        int checks;
        boolean closed;
        SlotProbe(GameTestHelper helper) { this.helper = helper; }
        void tick() {
            if (closed) return;
            try {
                helper.assertTrue(++ticks < 380, "Finisher slot probe timeout at " + step);
                if (power != null) power.setStamina(power.getMaxStamina());
                switch (step) {
                    case SETUP -> setup();
                    case COOLDOWN -> cooldown();
                    case PRESS_CHOP -> pressChop();
                    case WINDUP -> windup();
                    case RECOVERY -> recovery();
                    case DONE -> {}
                }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() {
            ServerLevel level = helper.getLevel();
            ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            user = new Player(level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "mih-chop-slot")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return false; }
            };
            user.setNoGravity(true); user.noPhysics = true; user.moveTo(origin.x, origin.y, origin.z, 0, 0);
            helper.assertTrue(level.addFreshEntity(user), "Setup: user added");
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(MadeInHeavenAddon.MADE_IN_HEAVEN.get())).status()
                    == StandPowerTransitions.Status.APPLIED, "Setup: public Stand grant");
            power.setResolveLevel(6);
            helper.assertTrue(MadeInHeavenAddon.MADE_IN_HEAVEN.get().summon(user, power), "Setup: native summon");
            stand = power.getSummonedStandEntity(); stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true); stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user);
            var unlocked = UNLOCKS.forceUnlock(power, "chop");
            helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
                    "Setup: public Chop unlock");
            heavy = power.getAbility("heavy_punch"); chop = power.getAbility("chop");
            helper.assertTrue(heavy != null && chop != null && chop.isStandFinisherOf != null
                    && chop.isStandFinisherOf.minFinisherValue() == 0.5F, "Setup: registered heavy punch and its 0.5 Chop finisher");
            stand.setFinisherMeter(0.75F);
            power.setAbilityCooldown("chop", 20);
            step = Step.COOLDOWN;
        }
        void cooldown() {
            helper.assertTrue(stand.getFinisherMeter() >= 0.5F && power.isAbilityOnCooldown("chop") && stand.getCurStandAction() == null,
                    "Setup: a full meter, Chop on its cooldown and an idle Stand; meter " + stand.getFinisherMeter());
            float meter = stand.getFinisherMeter();
            AbilityConditionCheck checked = power.updateAvailableMoves().getContextVariationContainer(heavy);
            check(checked != null && checked.ability == chop && !checked.conditionCheck.isPositive(),
                    "1.16 replaceAction: a full meter makes the heavy input Chop, refused while Chop cools down; selected " + selected(checked));
            BufferingState state = press(checked, heavy, HEAVY);
            check(!state.isActionSuccess && !state.shouldBuffer && stand.getCurStandAction() == null && stand.getFinisherMeter() == meter,
                    "the refused input starts no finisher-strength heavy punch and keeps the meter; action " + stand.getCurStandAction());
            power.setAbilityCooldown("chop", 0);
            helper.assertTrue(user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 0)), "Setup: Resolve drops Chop's cooldown(25)");
            stand.setFinisherMeter(0.75F);
            step = Step.PRESS_CHOP;
        }
        void pressChop() {
            // A regression above may have started an ordinary heavy punch; let it end first.
            if (stand.getCurStandAction() != null) return;
            AbilityConditionCheck checked = power.updateAvailableMoves().getContextVariationContainer(heavy);
            helper.assertTrue(checked != null && checked.ability == chop && checked.conditionCheck.isPositive() && user.hasEffect(ModStatusEffects.RESOLVE),
                    "Setup: with Resolve and a full meter the heavy input is a usable Chop; selected " + selected(checked));
            BufferingState state = press(checked, heavy, HEAVY);
            running = stand.getCurStandAction();
            helper.assertTrue(state.isActionSuccess && running instanceof MadeInHeavenChopAbility.MadeInHeavenChop
                    && running.getPhase() == ActionPhase.WINDUP && !power.isAbilityOnCooldown("chop") && stand.getFinisherMeter() < 0.5F,
                    "Setup: a Chop in its windup, no Chop cooldown under Resolve, and the meter spent below 0.5");
            AbilityInput.keyRelease(HEAVY, user);
            step = Step.WINDUP;
        }
        void windup() {
            helper.assertTrue(stand.getCurStandAction() == running && running.getPhase() == ActionPhase.WINDUP, "Setup: the Chop is still in its windup");
            attemptDuringChop();
            step = Step.RECOVERY;
        }
        void recovery() {
            if (stand.getCurStandAction() != running || running.isOver()) {
                check(false, "the first Chop was replaced or ended before its recovery, current " + stand.getCurStandAction());
                finish(); return;
            }
            if (running.getPhase() != ActionPhase.RECOVERY) return;
            attemptDuringChop();
            Ability punch = power.getAbility("punch");
            BufferingState state = press(power.updateAvailableMoves().getContextVariationContainer(punch), punch, LIGHT);
            check(state.isActionSuccess && stand.getCurStandAction() != running, "1.16 isFreeRecovery: a light punch still cuts the Chop's recovery");
            finish();
        }
        void attemptDuringChop() {
            AbilityConditionCheck checked = power.updateAvailableMoves().getContextVariationContainer(heavy);
            check(checked != null && checked.ability == chop,
                    "1.16 replaceAction: during a running Chop the heavy input is Chop again, meter " + stand.getFinisherMeter() + "; selected " + selected(checked));
            BufferingState state = press(checked, heavy, HEAVY);
            check(!state.isActionSuccess && !state.shouldBuffer && stand.getCurStandAction() == running,
                    "1.16 canClickDuringTask refuses another Chop during the running one, unqueued; success=" + state.isActionSuccess
                            + " buffered=" + state.shouldBuffer + " current=" + stand.getCurStandAction());
            AbilityInput.keyRelease(HEAVY, user);
        }
        // The server's order: the input gate, then the press that may buffer.
        BufferingState press(AbilityConditionCheck checked, Ability base, short key) {
            BufferingState state = BufferingState.clickCanBuffer();
            if (checked != null && AbilityInput.withConditionCheck(checked, user, InputMethod.CLICK)) {
                AbilityInput.keyPress(key, checked.ability, user, null, InputMethod.CLICK, 0, state, base.getAbilityId());
            }
            return state;
        }
        String selected(AbilityConditionCheck checked) {
            return checked == null ? "none" : checked.ability.name() + (checked.conditionCheck.isPositive() ? "/usable" : "/refused");
        }
        void check(boolean passed, String detail) {
            checks++; log((passed ? "SLOT PASS " : "SLOT FAIL ") + step + " " + detail);
            if (!passed) failures.add(step + ": " + detail);
        }
        void finish() {
            step = Step.DONE;
            log("SLOT SUMMARY checks=" + checks + " failures=" + failures.size() + " details=" + failures);
            close();
            helper.assertTrue(failures.isEmpty(), "Chop finisher slot failed; see SLOT SUMMARY");
            helper.succeed();
        }
        void close() {
            if (closed) return; closed = true;
            if (user != null) {
                AbilityInput.keyRelease(HEAVY, user); AbilityInput.keyRelease(LIGHT, user);
                if (power != null && power.isSummoned()) MadeInHeavenAddon.MADE_IN_HEAVEN.get().forceUnsummon(user, power);
                user.discard();
            }
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
    private static final class Suite implements GameTestListener {
        final GameTestHelper helper;
        final List<String> failures = new ArrayList<>();
        final List<Object> listeners = new ArrayList<>();
        Scene scene;
        RuntimeException eventFailure;
        int index;
        int checks;
        boolean closed;
        Suite(GameTestHelper helper) {
            helper.assertTrue(active == null, "Overlapping Chop admission suites");
            this.helper = helper; active = this; helper.testInfo.addListener(this);
        }
        void listen() {
            Consumer<EntityTickEvent.Pre> pre = event -> {
                if (scene != null && !closed) try { scene.pre(event.getEntity()); }
                catch (RuntimeException failure) { eventFailure = failure; }
            };
            Consumer<EntityTickEvent.Post> post = event -> {
                if (scene != null && !closed) try { scene.post(event.getEntity()); }
                catch (RuntimeException failure) { eventFailure = failure; }
            };
            listeners.addAll(List.of(pre, post));
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Pre.class, pre);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, post);
        }
        void tick() {
            if (closed) return;
            try {
                if (eventFailure != null) throw eventFailure;
                if (scene != null && scene.done) { scene.close(); scene = null; }
                if (scene == null) {
                    if (index == Case.values().length) {
                        log("SUMMARY fix=03 scenes=" + index + " checks=" + checks + " failures=" + failures.size() + " details=" + failures);
                        close(); helper.assertTrue(failures.isEmpty(), "Chop admission failed; see SUMMARY"); helper.succeed(); return;
                    }
                    scene = new Scene(this, Case.values()[index++]); scene.prepare();
                }
                scene.tick(); helper.assertTrue(++scene.ticks < 96, "Natural action timeout " + scene.kind);
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void check(boolean passed, String detail) {
            checks++; log((passed ? "PASS " : "FAIL ") + scene.kind + " " + detail);
            if (!passed) failures.add(scene.kind + ": " + detail);
        }
        void close() {
            if (closed) return; closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear(); if (scene != null) scene.close(); active = null;
            log("CLEANUP listeners=0");
        }
        @Override public void testStructureLoaded(GameTestInfo test) {}
        @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
        @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
    private static final class Scene implements AutoCloseable {
        final Suite suite;
        final Case kind;
        final ServerLevel level;
        final Map<BlockPos, BlockState> floor = new LinkedHashMap<>();
        final List<Entity> loose = new ArrayList<>();
        FinitePlayer user;
        StandPower power;
        StandEntity stand;
        TargetCow target;
        Ability chop;
        EntityActionInstance action;
        int ticks;
        int order;
        int hurtOrder;
        int impactOrder;
        int performerTicks;
        float beforeImpactStamina;
        float afterImpactStamina;
        boolean hurt;
        boolean pressed;
        boolean shortageSet;
        boolean done;
        boolean closed;
        Scene(Suite suite, Case kind) { this.suite = suite; this.kind = kind; level = suite.helper.getLevel(); }
        void prepare() {
            ChunkPos chunk = new ChunkPos(suite.helper.absolutePos(BlockPos.ZERO));
            Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5, suite.helper.absolutePos(BlockPos.ZERO).getY() + 8, chunk.getMiddleBlockZ() + 0.5);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 4; z++) {
                BlockPos pos = BlockPos.containing(origin.add(x, -1, z)); floor.put(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            }
            user = new FinitePlayer(this); user.noPhysics = true; user.getAbilities().invulnerable = false;
            user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); user.setHealth(200); add(user, origin);
            power = PowerClass.STAND.attachGet(user);
            premise(StandPowerTransitions.insert(power, new StandInstance(MadeInHeavenAddon.MADE_IN_HEAVEN.get())).status()
                    == StandPowerTransitions.Status.APPLIED, "public Stand grant");
            power.setResolveLevel(6);
            premise(MadeInHeavenAddon.MADE_IN_HEAVEN.get().summon(user, power), "native summon");
            stand = power.getSummonedStandEntity(); stand.summonLockTicks = 0; stand.gradualSummonWeaknessTicks = 0;
            stand.setCanFollowUser(false); stand.setNoGravity(true); stand.moveTo(origin.x, origin.y, origin.z, 0, 0); stand.setYHeadRot(0);
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(15);
            user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
            user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(12);
            user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
            user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
            StandEntity.updateStandStatAttributes(stand, user);
            user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
            unlock("chop"); unlock("grab_throw"); chop = power.getAbility("chop"); premise(chop != null, "registered Chop");
            target = new TargetCow(this); target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200);
            target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0); add(target, origin.add(0, 0.7, 1.5));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(target));
            log("START " + kind);
        }
        void tick() {
            if (!pressed && ticks >= 3) press();
        }
        void press() {
            pressed = true; power.setStamina(kind == Case.LOW_49_99 ? 49.99F : 50F); stand.updateStrengthMultipliers();
            stand.setFinisherMeter(0.75F);
            if (kind == Case.ZERO_SPEED) stand.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(0);
            if (kind == Case.ZERO_ENTITY_REACH) stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(0);
            if (kind == Case.ZERO_BLOCK_REACH) stand.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(0);
            if (kind == Case.COOLDOWN) power.setAbilityCooldown("chop", 20);
            if (kind == Case.DEAD_STAND) stand.setHealth(0);
            if (kind == Case.STUN_STAND) stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
            if (kind == Case.MISSING_ARMS) premise(power.getStandInstance().orElseThrow().removePart(StandPart.ARMS), "arms actually removed");
            if (kind == Case.GRAB_CONTEXT || kind == Case.CHECKED_CHOP_GRABBED)
                stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(target);
            premise(!user.isCreative() && !power.isStaminaInfinite() && JojoModConfig.getCommonConfigInstance(false).standStamina.get(), "finite noncreative stamina policy");
            float stored = power.serializeNBT(level.registryAccess()).getFloat("Stamina");
            premise(near(stored, kind == Case.LOW_49_99 ? 49.99F : 50F) && near(power.getStamina(), stored), "stored and effective stamina match requested boundary");
            double speed = stand.getAttackSpeed(), entityReach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), blockReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
            premise((speed == 0) == (kind == Case.ZERO_SPEED || kind == Case.STUN_STAND) && (entityReach == 0) == (kind == Case.ZERO_ENTITY_REACH)
                    && (blockReach == 0) == (kind == Case.ZERO_BLOCK_REACH), "actual effective melee values distinguish zero-stat case");
            var bind = power.getMoveset().controlScheme.defaultGroup.separateBinds.get("heavy_punch");
            premise(bind != null && bind.getFirst() == InputMethod.CLICK && bind.getSecond() instanceof InputKey key
                    && key.device == InputKey.RMB.device && key.keyCode == InputKey.RMB.keyCode, "registered RMB CLICK");
            AbilityConditionCheck checked;
            if (kind == Case.GRAB_CONTEXT) {
                checked = power.updateAvailableMoves().getContextVariationContainer(power.getAbility("heavy_punch"));
                premise(checked != null && checked.ability.name().equals("grab_heavy_punch")
                        && checked.ability.getAbilityUsageCategory() == AbilityUsageGroup.GRAB, "normal context replaces finisher with grab-heavy");
            } else {
                // This is also the server's available client-selected ability fallback, not a protected-method call.
                checked = power.updateAvailableMoves().getCheckedContainerFor(power, chop);
                premise(checked.ability == chop && chop.getAbilityUsageCategory() != AbilityUsageGroup.GRAB, "direct checked registered Chop");
            }
            boolean outer = AbilityInput.withConditionCheck(checked, user, InputMethod.CLICK);
            boolean outerExpected = kind != Case.COOLDOWN && kind != Case.DEAD_STAND && kind != Case.STUN_STAND && kind != Case.MISSING_ARMS;
            premise(outer == outerExpected, "outer cooldown/actor/parts checks remain separate");
            if (kind == Case.COOLDOWN) premise(power.getAbilityCooldown("chop") == 20, "actual cooldown20");
            if (kind == Case.DEAD_STAND) premise(!stand.isAlive(), "performer is actually dead");
            if (kind == Case.STUN_STAND) premise(ModStatusEffects.isStunned(stand), "performer is actually stunned");
            if (kind == Case.CHECKED_CHOP_GRABBED) premise(LivingComponentGrab.getEntityGrabbedBy(stand) == target, "actual accepted-port grabbed target");
            BufferingState buffer = BufferingState.clickOnly();
            if (outer) AbilityInput.keyPress(INPUT, checked.ability, user, null, InputMethod.CLICK, 0, buffer, power.getAbility("heavy_punch").getAbilityId());
            action = stand.getCurStandAction();
            boolean shouldStart = outerExpected && kind != Case.LOW_49_99 && kind != Case.CHECKED_CHOP_GRABBED;
            suite.check(buffer.isActionSuccess == shouldStart && (action != null) == shouldStart, "registered initial admission expected=" + shouldStart + " actual=" + buffer.isActionSuccess);
            if (action != null) premise((action instanceof MadeInHeavenChopAbility.MadeInHeavenChop) == (kind != Case.GRAB_CONTEXT), "actual Chop/grab action factory");
            suite.check(near(power.getStamina(), stored), "initial admission does not debit post-attack cost");
            log("PRESS " + kind + " stored=" + stored + " effective=" + power.getStamina() + " speed=" + speed + " entityReach=" + entityReach + " blockReach=" + blockReach
                    + " outer=" + outer + " success=" + buffer.isActionSuccess + " selected=" + checked.ability.name());
            if (kind != Case.WINDUP_SHORTAGE || action == null) done = true;
            else premise(action.getPhase() == ActionPhase.WINDUP, "shortage is introduced only after real WINDUP started");
        }
        void pre(Entity entity) {
            if (kind != Case.WINDUP_SHORTAGE || entity != stand || action == null || done || closed) return;
            if (!shortageSet) {
                premise(action.getPhase() == ActionPhase.WINDUP, "natural performer still in WINDUP");
                power.setStamina(10); shortageSet = true;
                premise(power.getStamina() == 10 && power.serializeNBT(level.registryAccess()).getFloat("Stamina") == 10, "real late shortage stored/effective10");
                log("LATE-SHORTAGE phase=" + action.getPhase() + " effective=" + power.getStamina());
            }
            beforeImpactStamina = power.getStamina(); performerTicks++; order++;
        }
        void post(Entity entity) {
            if (kind != Case.WINDUP_SHORTAGE || entity != stand || action == null || done || closed || action.punchedTarget == null) return;
            afterImpactStamina = power.getStamina(); impactOrder = ++order;
            premise(action.punchedTarget.getMainEntity() == target, "natural target identity");
            premise(shortageSet && performerTicks > 0 && beforeImpactStamina < 50, "actual shortage persists until natural impact");
            suite.check(hurt && hurtOrder > 0 && hurtOrder < impactOrder, "initial-only stamina gate does not cancel a later poor WINDUP");
            suite.check(afterImpactStamina == 0 && beforeImpactStamina == 10, "unchanged consumeStamina50 remains post-attack and clamps late shortage10 to0");
            log("IMPACT ticks=" + performerTicks + " staminaBefore=" + beforeImpactStamina + " after=" + afterImpactStamina + " hurt=" + hurt);
            done = true;
        }
        void add(Entity entity, Vec3 position) {
            entity.setNoGravity(true); entity.moveTo(position.x, position.y, position.z, 0, 0);
            premise(level.addFreshEntity(entity), "native entity added"); loose.add(entity);
        }
        void unlock(String name) {
            var result = UNLOCKS.forceUnlock(power, name);
            premise(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "public unlock " + name);
        }
        void premise(boolean condition, String detail) { suite.helper.assertTrue(condition, kind + " premise: " + detail); }
        @Override public void close() {
            if (closed) return; closed = true;
            if (user != null) AbilityInput.keyRelease(INPUT, user);
            if (stand != null) stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get()).setGrabTarget(null);
            if (power != null && power.isSummoned()) MadeInHeavenAddon.MADE_IN_HEAVEN.get().forceUnsummon(user, power);
            for (Entity entity : loose) entity.discard(); floor.forEach(level::setBlockAndUpdate);
        }
    }
    private static final class FinitePlayer extends Player {
        FinitePlayer(Scene scene) { super(scene.level, BlockPos.ZERO, 0, new GameProfile(UUID.randomUUID(), "mih-chop-admission")); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
    }
    private static final class TargetCow extends Cow {
        final Scene scene;
        TargetCow(Scene scene) { super(EntityType.COW, scene.level); this.scene = scene; }
        @Override public boolean hurt(DamageSource source, float amount) {
            boolean result = super.hurt(source, amount);
            if (scene.kind == Case.WINDUP_SHORTAGE && source.getDirectEntity() == scene.stand) {
                scene.hurt = result; scene.hurtOrder = ++scene.order;
            }
            return result;
        }
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 0.0002; }
    private static String sha(Class<?> type) {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        } catch (Exception failure) { throw new IllegalStateException("Missing native class " + type.getName(), failure); }
    }
    private static void log(String message) { JojoMod.getLogger().info("D-MIH-CHOP-ADMISSION " + message); }
}

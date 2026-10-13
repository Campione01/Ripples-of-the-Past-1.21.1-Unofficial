package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.RetryOptions;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreativeTimeStopDurationGameTests {
    private static final int OPENING_TICKS = 35;
    private static final short KEY = 104;
    private static final PowerSkillUnlocks.Owner TEST_UNLOCKS = PowerSkillUnlocks.register(
            JojoMod.resLoc(CreativeTimeStopDurationGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));

    private CreativeTimeStopDurationGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 220, batch = GameTestBatches.TIME_STOP)
    public static void starPlatinumCreativeFreshFullChargeExpiresNatively(GameTestHelper helper) {
        startCast(helper, "star_platinum", 5, false);
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = GameTestBatches.TIME_STOP)
    public static void starPlatinumCreativeTrainedFullChargeExpiresNatively(GameTestHelper helper) {
        startCast(helper, "star_platinum", 100, false);
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = GameTestBatches.TIME_STOP)
    public static void theWorldCreativeFreshFullChargeExpiresNatively(GameTestHelper helper) {
        startCast(helper, "the_world", 5, false);
    }

    @GameTest(template = "empty", timeoutTicks = 220, batch = GameTestBatches.TIME_STOP)
    public static void theWorldCreativeTrainedFullChargeExpiresNatively(GameTestHelper helper) {
        startCast(helper, "the_world", 100, false);
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = GameTestBatches.TIME_STOP)
    public static void starPlatinumCreativePartialReleaseRemainsProportional(GameTestHelper helper) {
        startCast(helper, "star_platinum", 25, true);
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = GameTestBatches.TIME_STOP)
    public static void theWorldCreativePartialReleaseRemainsProportional(GameTestHelper helper) {
        startCast(helper, "the_world", 25, true);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
    public static void starPlatinumSurvivalBlinkBudgetRemainsScaled(GameTestHelper helper) {
        assertCallerBudgets(helper, "star_platinum", false);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
    public static void theWorldSurvivalBlinkAndPunchBudgetsRemainScaled(GameTestHelper helper) {
        assertCallerBudgets(helper, "the_world", false);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
    public static void starPlatinumCreativeBlinkBudgetUsesSavedDuration(GameTestHelper helper) {
        assertCallerBudgets(helper, "star_platinum", true);
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
    public static void theWorldCreativeBlinkAndPunchBudgetsUseSavedDuration(GameTestHelper helper) {
        assertCallerBudgets(helper, "the_world", true);
    }

    @GameTest(template = "empty", timeoutTicks = 140, batch = GameTestBatches.TIME_STOP)
    public static void manualResumeFixtureDoesNotAdvanceNativeCreativeTimeStop(GameTestHelper helper) {
        startCast(helper, "the_world", 5, false, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
    public static void frameworkFailureReleasesTheOwnedTimeStopScene(GameTestHelper helper) {
        Scene[] owned = new Scene[1];
        StandEntity[] performer = new StandEntity[1];
        boolean[] observed = new boolean[1];
        String marker = "S04-05 deterministic nested framework failure";
        GameTestInfo outer = helper.testInfo;
        var structure = outer.getStructureBlockEntity();
        String outerName = outer.getTestName();
        TestFunction function = new TestFunction(GameTestBatches.TIME_STOP, "s04_owned_failure_probe",
                outer.getStructureName(), Rotation.NONE, 80, 0, false, inner -> {
                    Scene scene = new Scene(inner, "star_platinum", 100, true, false);
                    owned[0] = scene;
                    scene.setUp();
                    scene.press();
                    performer[0] = (StandEntity) scene.action.getPerformer();
                    inner.runAfterDelay(50, () -> {
                        inner.assertTrue(scene.started != null && scene.state.getInstance(scene.player.getId()).isPresent()
                                && scene.player.hasEffect(ModStatusEffects.TIME_STOP), "S04-05 failure probe never reached live PERFORM");
                        throw new GameTestAssertException(marker);
                    });
                });
        GameTestInfo probe = new GameTestInfo(function, Rotation.NONE, helper.getLevel(), RetryOptions.noRetries());
        try {
            // Bind an already-loaded structure; never place, clear or register a nested runner.
            var position = GameTestInfo.class.getDeclaredMethod("setStructureBlockPos", BlockPos.class);
            position.setAccessible(true);
            position.invoke(probe, outer.getStructureBlockPos());
            var block = GameTestInfo.class.getDeclaredField("structureBlockEntity");
            block.setAccessible(true);
            block.set(probe, structure);
            var placed = GameTestInfo.class.getDeclaredField("placedStructure");
            placed.setAccessible(true);
            placed.setBoolean(probe, true);
            probe.startExecution(0);
        }
        catch (ReflectiveOperationException error) {
            throw new IllegalStateException("S04-05 cannot bind the native failure probe", error);
        }
        helper.onEachTick(() -> {
            if (observed[0]) return;
            try {
                probe.tick(null);
                helper.assertTrue(outer.getStructureBlockEntity() == structure && outer.getTestName().equals(outerName)
                        && !outer.isDone(), "S04-05 nested probe reset its outer test");
                if (!probe.isDone()) return;
                Scene scene = owned[0];
                observed[0] = true;
                JojoMod.getLogger().info("S04-05 FRAMEWORK-FAILURE expected=" + marker.equals(probe.getError().getMessage())
                        + " closed=" + scene.closed + " listeners=" + scene.listeners.size()
                        + " playerRemoved=" + scene.player.isRemoved() + " standRemoved=" + performer[0].isRemoved()
                        + " instanceLeft=" + scene.state.getInstance(scene.player.getId()).isPresent());
                helper.assertTrue(probe.hasFailed() && marker.equals(probe.getError().getMessage())
                                && scene.closed && scene.listeners.isEmpty() && scene.player.isRemoved()
                                && performer[0].isRemoved() && scene.state.getInstance(scene.player.getId()).isEmpty()
                                && !scene.player.hasEffect(ModStatusEffects.TIME_STOP)
                                && !scene.player.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.containsKey(KEY)
                                && LivingComponentAction.getCurEntityAction(performer[0]) == null,
                        "S04-05 framework failure did not release the owned Scene");
                int stoppedNativeTicks = scene.nativeTicks;
                helper.runAfterDelay(1, () -> {
                    helper.assertTrue(scene.nativeTicks == stoppedNativeTicks,
                            "S04-05 ended scene kept observing native level ticks");
                    helper.succeed();
                });
            }
            catch (RuntimeException | Error error) {
                observed[0] = true;
                if (owned[0] != null) owned[0].close();
                throw error;
            }
        });
    }

    private static void requireNativeClassIdentity(GameTestHelper helper) {
        requireNativeClassIdentity(helper, TimeStopLearning.class, TimeStopAbility.class);
    }

    private static void requireNativeClassIdentity(GameTestHelper helper, Class<?>... types) {
        for (Class<?> type : types) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var input = type.getResourceAsStream(resource)) {
                helper.assertTrue(input != null, "S04-05 cannot read loaded native class resource " + type.getName());
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                var origin = type.getProtectionDomain().getCodeSource();
                String expected = System.getProperty("rotp.s04.expected." + type.getSimpleName());
                JojoMod.getLogger().info("S04-05 LOADED class=" + type.getName() + " source="
                        + (origin != null ? origin.getLocation() : "unknown") + " resourceSha256=" + sha);
                helper.assertTrue(expected == null || expected.equals(sha) && origin != null,
                        "S04-05 loaded native class does not match the private runtime receipt: " + type.getName());
            }
            catch (Exception error) {
                throw new IllegalStateException("S04-05 native class identity failed", error);
            }
        }
    }

    private static void startCast(GameTestHelper helper, String stand, int savedTicks, boolean partial) {
        startCast(helper, stand, savedTicks, partial, false);
    }

    private static void startCast(GameTestHelper helper, String stand, int savedTicks, boolean partial,
            boolean manualResumeOverlap) {
        Scene scene = new Scene(helper, stand, savedTicks, true, partial);
        scene.manualResumeOverlap = manualResumeOverlap;
        try {
            scene.setUp();
            scene.press();
            helper.onEachTick(scene::observeNativeExecution);
        }
        catch (RuntimeException | Error error) {
            scene.close();
            throw error;
        }
    }

    private static void assertCallerBudgets(GameTestHelper helper, String stand, boolean creative) {
        Scene scene = new Scene(helper, stand, 25, creative, false);
        try {
            scene.setUp();
            Ability ability = scene.power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
            helper.assertTrue(ability instanceof TimeStopBlinkAbility, "S04-05 missing live blink: " + stand);
            TimeStopBlinkAbility blink = (TimeStopBlinkAbility) ability;
            float tickCost = 9.0F * 100.0F / 25.0F * 0.8F;
            int blinkTicks = blink.getMaxImpliedTicks(scene.power);
            float actualTickCost = blink.getBlinkStaminaCostTicking(scene.power, TimeStopLearning.TIME_STOP);
            int expectedBlink = creative ? 25 : Mth.clamp(
                    Mth.floor((scene.power.getStamina() - 180.0F) / tickCost), 5, 25);
            boolean hasPunch = scene.power.getMoveset().getAbility("ts_punch") != null;
            int punchTicks = hasPunch ? TimeStopLearning.getAffordableTsPunchTimeStopTicks(scene.power) : -1;
            int expectedPunch = creative ? 25 : Mth.clamp(
                    Mth.floor((scene.power.getStamina() - 90.0F) / tickCost), 0, 25);
            float punchBase = hasPunch ? TimeStopLearning.getTsPunchTimeStopBaseStaminaCost(scene.power) : 0;
            float punchTick = hasPunch ? TimeStopLearning.getTsPunchTimeStopStaminaCostTicking(scene.power) : 0;
            TranslatableContents name = (TranslatableContents) scene.timeStop.getName(scene.power).getContents();
            String nameKey = "jojo_ripples.ability." + stand + "_time_stop";
            JojoMod.getLogger().info("S04-05 CALLERS stand=" + stand + " creative=" + creative
                    + " saved=25 blinkTicks=" + blinkTicks + " expectedBlink=" + expectedBlink
                    + " blinkStart=" + blink.getBlinkStaminaCost(scene.power) + " blinkTick=" + actualTickCost
                    + " hasPunch=" + hasPunch + " punchTicks=" + punchTicks + " expectedPunch=" + expectedPunch
                    + " punchStart=" + punchBase + " punchTick=" + punchTick + " name=" + name.getKey());
            helper.assertTrue(blinkTicks == expectedBlink
                            && Math.abs(blink.getBlinkStaminaCost(scene.power) - 180.0F) < 0.001F
                            && Math.abs(actualTickCost - tickCost) < 0.001F
                            && name.getKey().equals(nameKey) && name.getArgs().length == 1
                            && name.getArgs()[0].equals("1.25")
                            && hasPunch == stand.equals("the_world")
                            && (!hasPunch || punchTicks == expectedPunch
                                    && Math.abs(punchBase - 90.0F) < 0.001F
                                    && Math.abs(punchTick - tickCost) < 0.001F),
                    "S04-05 shared duration callers: " + stand + " creative=" + creative
                            + " blink=" + blinkTicks + "/" + expectedBlink
                            + " punch=" + punchTicks + "/" + expectedPunch + " tickCost=" + actualTickCost);
        }
        finally {
            scene.close();
        }
        helper.succeed();
    }

    private static final class Scene implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final String stand;
        private final int savedTicks;
        private final boolean creative;
        private final boolean partial;
        private final List<Object> listeners = new ArrayList<>();
        private Player player;
        private StandPower power;
        private TimeStopAbility timeStop;
        private TimeStopState state;
        private EntityActionInstance action;
        private TimeStopState.Instance started;
        private TimeStopState.Instance removed;
        private TimeStopLifecycleEvent.RemovalReason removalReason;
        private ActionPhase phaseAtStart;
        private int nativeTicks;
        private long inputGeneration;
        private int pressTick;
        private int startTick = -1;
        private int removalTick = -1;
        private int effectDurationAtStart;
        private float staminaAtStart;
        private boolean manualResumeOverlap;
        private boolean manualResumeBodyInvoked;
        private int manualResumeClockAdvance = -1;
        private boolean released;
        private boolean closed;

        private Scene(GameTestHelper helper, String stand, int savedTicks, boolean creative, boolean partial) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.stand = stand;
            this.savedTicks = savedTicks;
            this.creative = creative;
            this.partial = partial;
            helper.testInfo.addListener(this);
        }

        private void setUp() {
            requireNativeClassIdentity(helper);
            player = GameTestPlayers.makeServerMockPlayer(helper, creative ? GameType.CREATIVE : GameType.SURVIVAL);
            player.getAbilities().instabuild = creative;
            player.setNoGravity(true);
            player.noPhysics = true;
            ChunkPos cell = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
            player.moveTo(cell.getMiddleBlockX() + 0.5, helper.absolutePos(new BlockPos(0, 2, 0)).getY(),
                    cell.getMiddleBlockZ() + 0.5, 0, 0);
            helper.assertTrue(level.addFreshEntity(player), "S04-05 could not add logical-server player");
            power = PowerClass.STAND.attachGet(player);
            StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand));
            helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
                    == StandPowerTransitions.Status.APPLIED, "S04-05 could not grant " + stand);
            power.setResolveLevel(power.getMaxResolveLevel());
            TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
            power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP,
                    savedTicks - 5.0F, TimeStopLearning.getMaxTrainingPoints(power), power);
            power.setStamina(power.getMaxStamina());
            Ability ability = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
            helper.assertTrue(ability instanceof TimeStopAbility && power.isUserCreative() == creative
                            && TimeStopLearning.getSavedTimeStopTicks(power) == savedTicks
                            && power.isAbilityUnlocked(TimeStopLearning.TIME_STOP),
                    "S04-05 premises: " + stand + " creative=" + power.isUserCreative()
                            + " saved=" + TimeStopLearning.getSavedTimeStopTicks(power));
            timeStop = (TimeStopAbility) ability;
            state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
            Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
                if (owns(event)) {
                    event.setInstance(event.getInstance().withArea(new ChunkPos(50300 + player.getId() * 2, 50300), 1));
                }
            };
            Consumer<LevelTickEvent.Pre> countNative = event -> {
                if (!closed && event.getLevel() == level) nativeTicks++;
            };
            Consumer<TimeStopLifecycleEvent.Added> added = event -> {
                if (owns(event)) {
                    started = event.getInstance();
                    startTick = nativeTicks;
                    phaseAtStart = action.getPhase();
                    MobEffectInstance immunity = player.getEffect(ModStatusEffects.TIME_STOP);
                    effectDurationAtStart = immunity != null ? immunity.getDuration() : -1;
                    staminaAtStart = power.getStamina();
                    JojoMod.getLogger().info("S04-05 ADDED " + describe());
                }
            };
            Consumer<TimeStopLifecycleEvent.Removed> ended = event -> {
                if (owns(event)) {
                    removed = event.getInstance();
                    removalReason = event.getReason();
                    // Removal runs inside HIGHEST LevelTick.Pre, before this observer at LOWEST.
                    removalTick = nativeTicks + 1;
                    JojoMod.getLogger().info("S04-05 REMOVED " + describe());
                }
            };
            Consumer<EntityLeaveLevelEvent> left = event -> {
                if (!closed && action != null && event.getEntity() == action.getPerformer()) {
                    JojoMod.getLogger().info("S04-05 UNEXPECTED-PERFORMER-LEAVE " + describe()
                            + " removal=" + event.getEntity().getRemovalReason() + " frames="
                            + StackWalker.getInstance().walk(frames -> frames.limit(12)
                                    .map(StackWalker.StackFrame::toString).toList()));
                }
            };
            listeners.add(isolate);
            listeners.add(countNative);
            listeners.add(added);
            listeners.add(ended);
            listeners.add(left);
            NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, isolate);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LevelTickEvent.Pre.class, countNative);
            NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.Added.class, added);
            NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.Removed.class, ended);
            NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, left);
        }

        private boolean owns(TimeStopLifecycleEvent event) {
            return !closed && event.getLevel() == level && event.getInstance().userId() == player.getId();
        }

        private void press() {
            helper.assertTrue(AbilityInput.withConditionCheck(timeStop, player), "S04-05 real admission refused " + stand);
            BufferingState buffering = new BufferingState();
            pressTick = nativeTicks;
            HeldInputEntry input = AbilityInput.keyPress(KEY, timeStop, player, null, InputMethod.HOLD, 0, buffering, null);
            helper.assertTrue(buffering.isActionSuccess && input != null && input.action instanceof EntityActionInstance,
                    "S04-05 AbilityInput did not install the held action: " + stand);
            action = (EntityActionInstance) input.action;
            inputGeneration = input.generation;
            int charge = stand.equals("star_platinum") ? 40 : 20;
            helper.assertTrue(action.getPhase() == ActionPhase.BUTTON_CHARGE && action.getCurPhaseLength() == charge
                            && action.getPhaseTick() == 0 && !player.hasEffect(ModStatusEffects.TIME_STOP)
                            && state.getInstance(player.getId()).isEmpty(),
                    "S04-05 real charge did not begin at 0/" + charge + ": " + describe());
            JojoMod.getLogger().info("S04-05 PRESS " + describe());
        }

        private void observeNativeExecution() {
            if (closed) return;
            try {
                if (partial && !released && action.getPhase() == ActionPhase.BUTTON_CHARGE
                        && action.getPhaseTick() >= action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) / 2) {
                    released = true;
                    helper.assertTrue(AbilityInput.keyReleaseFromNetwork(KEY, player, inputGeneration)
                            == AbilityInput.ReleaseResult.RELEASED, "S04-05 real partial release was refused");
                }
                if (started == null) {
                    helper.assertTrue(nativeTicks - pressTick <= 42,
                            "S04-05 native action never performed: " + describe());
                    return;
                }
                if (manualResumeOverlap && !manualResumeBodyInvoked) {
                    invokeOriginalManualResumeBody();
                }
                int duration = partial ? Mth.ceil(savedTicks / 2.0F) : savedTicks;
                int elapsed = nativeTicks - startTick;
                if (elapsed < OPENING_TICKS + duration) {
                    helper.assertTrue(removed == null && state.getInstance(player.getId()).isPresent()
                                    && state.isTimeStopped(started.centerPos()),
                            "S04-05 time resumed before its native deadline: " + describe());
                    return;
                }
                JojoMod.getLogger().info("S04-05 DEADLINE " + describe());
                int charge = stand.equals("star_platinum") ? 40 : 20;
                helper.assertTrue(started.totalTicks() == duration && started.ticksLeft() == duration
                                && started.ticksPassed() == -OPENING_TICKS
                                && effectDurationAtStart == OPENING_TICKS + duration
                                && phaseAtStart == (partial ? ActionPhase.BUTTON_CHARGE : ActionPhase.PERFORM)
                                && (partial || startTick - pressTick >= charge && startTick - pressTick <= charge + 1)
                                && staminaAtStart == power.getMaxStamina()
                                && removed != null && removalReason == TimeStopLifecycleEvent.RemovalReason.EXPIRED
                                && removalTick - startTick == OPENING_TICKS + duration
                                && removed.ticksPassed() == duration
                                && state.getInstance(player.getId()).isEmpty()
                                && !state.isTimeStopped(started.centerPos())
                                && !player.hasEffect(ModStatusEffects.TIME_STOP)
                                && !power.isAbilityOnCooldown(TimeStopLearning.TIME_STOP)
                                && !power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK)
                                && (!manualResumeOverlap || manualResumeBodyInvoked && manualResumeClockAdvance == 0),
                        "S04-05 real Creative cast must expire after saved duration + 35: " + describe());
                close();
                helper.succeed();
            }
            catch (RuntimeException | Error error) {
                close();
                throw error;
            }
        }

        private void invokeOriginalManualResumeBody() {
            requireNativeClassIdentity(helper, TimeStopProgressionGameTests.class, CreativeTimeStopDurationGameTests.class);
            TimeStopState.Instance before = state.getInstance(player.getId()).orElse(null);
            int nativeBefore = nativeTicks;
            long gameTimeBefore = level.getGameTime();
            helper.assertTrue(before != null && nativeTicks - startTick <= 1,
                    "S04-05 overlap did not begin at the live native cast's start: " + describe());
            manualResumeBodyInvoked = true;
            TimeStopProgressionGameTests.assertManualResumeKeepsTheOriginalElevenTicks(helper);
            TimeStopState.Instance after = state.getInstance(player.getId()).orElse(null);
            helper.assertTrue(after != null, "S04-05 original manual-resume body removed the foreign cast");
            manualResumeClockAdvance = after.ticksPassed() - before.ticksPassed();
            JojoMod.getLogger().info("S04-05 OVERLAP nativeBefore=" + nativeBefore + " nativeAfter=" + nativeTicks
                    + " gameTimeBefore=" + gameTimeBefore + " gameTimeAfter=" + level.getGameTime()
                    + " foreignClockAdvance=" + manualResumeClockAdvance + " before=" + before + " after=" + after);
            helper.assertTrue(nativeTicks == nativeBefore && level.getGameTime() == gameTimeBefore,
                    "S04-05 synchronous manual-resume body advanced the native level clock");
        }

        private String describe() {
            TimeStopState.Instance current = state != null && player != null
                    ? state.getInstance(player.getId()).orElse(null) : null;
            var performer = action != null ? action.getPerformer() : null;
            var liveAction = performer != null ? LivingComponentAction.getCurEntityAction(performer) : null;
            return "stand=" + stand + " saved=" + savedTicks + " partial=" + partial + " creative=" + creative
                    + " nativeTicks=" + nativeTicks + " press=" + pressTick + " start=" + startTick
                    + " removal=" + removalTick + " phaseAtStart=" + phaseAtStart
                    + " initialTotal=" + (started != null ? started.totalTicks() : -1)
                    + " initialEffect=" + effectDurationAtStart + " current=" + current + " reason=" + removalReason
                    + " playerAge=" + (player != null ? player.tickCount : -1)
                    + " performer=" + (performer != null ? performer.getId() + "/" + performer.position()
                            + "/age" + performer.tickCount + "/alive" + performer.isAlive() : "null")
                    + " entityTicking=" + (performer != null && level.isPositionEntityTicking(performer.blockPosition()))
                    + " actionPhase=" + (action != null ? action.getPhase() + "/" + action.getPhaseTick() + "/over" + action.isOver() : "null")
                    + " liveSame=" + (liveAction == action) + " live=" + (liveAction != null ? liveAction.getPhase() : "null")
                    + (manualResumeOverlap ? " manualResumeClockAdvance=" + manualResumeClockAdvance : "");
        }

        private void close() {
            if (closed) return;
            closed = true;
            for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
            listeners.clear();
            if (action != null && action.getPerformer() != null) {
                LivingComponentAction.getComponent(action.getPerformer()).setAction(null, SyncType.NO_SYNC);
            }
            if (player != null) {
                AbilityInput.keyRelease(KEY, player);
                if (state != null) state.removeInstance(player.getId());
                player.removeEffect(ModStatusEffects.TIME_STOP);
                StandEntity summoned = power != null ? power.getSummonedStandEntity() : null;
                if (summoned != null) summoned.discard();
                player.discard();
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
    }
}
